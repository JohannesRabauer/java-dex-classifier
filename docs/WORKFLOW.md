# How it works

**Idea:** show a card to the webcam, get its name. No photos taken by hand.

## The whole flow

```mermaid
flowchart TB
    subgraph T["① TRAIN  (button press, ~75 s)"]
        direction TB
        P["20 card PNGs"] --> S["Draw fake webcam pictures<br/>2500 total"]
        S --> R1["ResNet18<br/>(frozen, pretrained)<br/>picture → 512 numbers"]
        R1 --> H["Train one small layer<br/>512 → 20 cards"]
        H --> M[("data/model<br/>head.jd")]
    end

    J{{"JobRunr<br/>runs ① in the background<br/>+ progress and logs"}} -.-> T

    subgraph D["② DETECT  (live)"]
        direction TB
        W["Webcam frame<br/>(3:4 window)"] --> R2["ResNet18<br/>same one"]
        R2 --> L["Layer from head.jd<br/>→ probabilities"]
        L --> O{"Best ≥ 80 %?"}
        O -- yes --> Y["Card name"]
        O -- no --> N["'Not sure'"]
    end

    M ==> L
```

## ① Train

| What | Detail |
|---|---|
| **Trigger** | Button **Train the model** or `POST /api/jobs/train` |
| **Never** | Not at startup, not on a schedule |
| **Data** | Drawn from `cards/png`, nothing downloaded |
| **Per card** | 100 to train, 25 to check |
| **Check set** | Pictures the model never trained on |
| **Time** | ~75 s drawing + ResNet, ~1 s training |
| **Output** | `data/model/` |

**Fake pictures contain:** perspective, thumb over the card, glare, blur, noise, JPEG artefacts, random room.

**Why so fast?** Only the small last layer learns. ResNet18 never changes.

## JobRunr's job

- Takes the training **off the web request**
- Request returns at once (`202 Accepted`)
- Job stored in H2 (`data/jobrunr-db`)
- **Dashboard** shows progress bar, logs, errors: http://localhost:8000/dashboard
- Retries: **none**

## ② Detect

1. Browser crops the card window, sends JPEG to `/api/predict`
2. ResNet18 → 512 numbers
3. Layer → 20 probabilities
4. Best below 80 %? → **"Not sure"**
5. New model saved? → reloaded automatically

No model yet → `409`.

## Where to look in the code

| Step | Class |
|---|---|
| Start job | `TrainingController` |
| The job | `CardTrainingJob` |
| Draw pictures | `CardImageSynthesizer` |
| ResNet18 | `Backbone` |
| Fit the layer | `HeadTrainer` |
| Classify | `CardClassifier`, `CardPredictionService` |

## Run

```bash
./mvnw spring-boot:run
```

JDK 25. First training downloads PyTorch + ResNet18 once.

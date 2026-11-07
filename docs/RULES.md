# Java-Dex rules

A quick game for two players. One round takes a few seconds, a full game about five minutes.

## Setup

Shuffle the 20 cards and deal 10 to each player. Keep your cards in a face-down pile and look only at the top card.

## A round

1. The starting player picks one stat on their top card and says it out loud: Startup, Clean, Community or Age.
2. Both players turn over their top card.
3. The card with the higher number in that stat wins. The winner takes both cards and puts them under their own pile.
4. If both numbers are the same, both cards go under their owners' piles and the same player picks again.
5. The winner of the round picks the stat in the next round.

## The stats

| Stat | Meaning | Scale |
|---|---|---|
| Startup | How fast it gets going. Higher is faster. | 1 to 10 |
| Clean | How little boilerplate and mess it brings. Higher is cleaner. | 1 to 10 |
| Community | How many people know it and help each other with it. | 1 to 10 |
| Age | Years since the first release. Higher is older. | 1 to 35 |

In every stat the higher number wins. There are no reversed stats.

## End of the game

You win when you hold all 20 cards, or when you hold more cards after 10 rounds.

## Playing with the webcam

In the session, the classifier looks at the card you hold up and tells the game which card it is. The game then looks up the stats in `cards/cards.json`, plays the computer's top card against it and shows the result.

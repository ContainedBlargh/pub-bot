# pub-bot
A discord-bot that lets user subscribe and **pub**lish to particular topics.

This bot was written using [the Javacord framework](https://github.com/Javacord/Javacord).

## Installation

Create a discord app on the [discord developer portal](https://discord.com/developers/applications).

Create a bot as well.

Setup a folder with permissions for the program to read/write and a valid `config.ini` file on your server/computer.

Example:
```
;token, get this when you make your discord-bot.
token = whatEveRYouRTOkenMIgHTBe
;permissions bit mask, I currently use:
permmissions = 2147690592
```

Download the release of this github page or build the `.jar` file yourself.

Run the program using `java -jar PubBot.jar`.

Make sure that you are running from within this folder so that the config file can still be accessed.


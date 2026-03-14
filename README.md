# TinkRarityLoot
Tinkers Construct, randomized loot crate paired with a rarity system(Mine and Slash work in progress)


Items have a Level range from 0-infinity

Sources of aquisition is purely cosmetic in this version.

7 Rarities:
  1. common
  2. uncommon
  3. rare
  4. epic
  5. unique
  6. Legendary
  7. mythic
     
3 different types of crate:
  1. lootbox_melee
  2. lootbox_ranged
  3. lootbox_tool

The Tool lootbox is didsabled from mob kill aquisition in the config.

Commands to test the loot crate:
  /give @s tinkrarityloot:lootbox_melee{trl_rarity:"rare",trl_level:20,trl_source:"test"}

Command to check stored data on a component or completed assembly:
  /trl dumptool

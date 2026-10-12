# Citizen presets (schema 3)

Smart Phone (`createapparel:smart_phone`) converts an adult or baby vanilla Villager immediately on right click, from either hand. It is reusable, has no durability, and is available in the Create: Apparel creative tab. Conversion runs on the server. A successful conversion emits five green happy-villager particles and plays `minecraft:item.armor.equip_leather` once; failures and duplicate operations emit neither. Wandering Traders and other merchant types are excluded. Citizen creation must succeed before the original Villager is removed. Names, position, rotation, baby category, motion and common stationary flags are retained; trades, profession, gossip and POI are not migrated.

## Data pack layout

```text
data/createapparel/citizen/body_presets/adult/<id>.json
data/createapparel/citizen/body_presets/child/<id>.json
data/createapparel/citizen/garment_presets/<id>.json
```

These paths are relative to the data pack root. Only the `createapparel` namespace is supported. The reference `createapparel:casual_01` addresses `citizen/garment_presets/casual_01.json`; the directory is not part of the preset ID. Adult and child Body IDs have separate catalogs. Definitions are validated on server startup and `/reload`; existing Citizens keep their saved appearance and clothes. No client resources or textures are loaded by the server.

## Body preset

All seven fields are required. No age or gender field is accepted; age comes from the directory and the original Villager.

```json
{
  "arm_profile": "slim",
  "chest_profile": "bust",
  "skin_tone": ["fair", "natural", "healthy"],
  "hair_style": "createapparel:long_straight",
  "hair_color": ["black", "brown"],
  "eye_color": ["black", "brown"],
  "garment_presets": ["createapparel:female_casual_01", "createapparel:female_formal_01"]
}
```

`arm_profile`: `classic`, `slim`. `chest_profile`: `flat`, `bust`. `skin_tone`: `snow`, `very_fair`, `fair`, `natural`, `healthy`, `tanned`, `light_brown`, `brown`, `dark`. `garment_presets` must be nonempty, unique, and contain namespaced IDs. Missing garment references are warned and excluded from that body's garment draw. `createapparel:default` is reserved for fallback, for both Body and Garment presets, and never enters normal draws. Bodies with no available non-default outfit references are also excluded. Eligible bodies are drawn uniformly within the age group, followed by a uniform draw from the chosen body's available non-default references. The complete compiled fallback is used only when that age group has no eligible bodies, or if generating the selected clothes fails.

`skin_tone`, `hair_color` and `eye_color` accept a single string or a nonempty array of unique strings. Each field is drawn independently and uniformly from its declared choices when conversion occurs. `arm_profile`, `chest_profile` and `hair_style` remain single values. Arrays contain at most 256 entries; every member is validated before the preset enters the catalog. A malformed member rejects the entire preset. Schema 1 string values remain compatible.

The server can verify these built-in hairstyles: `short`, `bob`, `long_straight`, `ponytail`, `twintails`, `centerpart`, `mash`, `medium`, `long_wave`, `bald`, all prefixed with `createapparel:`. Resource-pack-only custom hair cannot be verified on a dedicated server and is rejected in presets. The existing spawn command still accepts custom namespaced hair IDs.

Hair and eye colors use the existing FabricColor palette, exactly like garments. Accepted names:

`white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black`, `cream`, `beige`, `greige`, `tan`, `navy`, `cerulean`, `sky`, `olive`, `khaki`, `forest`, `mint`, `burgundy`, `coral`, `rose`, `mustard`, `lavender`, `plum`.

Color names are case-sensitive. Presets reject numeric RGB, `#RRGGBB`, `0xRRGGBB`, and unknown names. Entity NBT and synchronization retain integer RGB, including older saved colors. The legacy default appearance, DyeColor API and legacy NBT color-name interpretation remain compatible. New spawn-command named/random colors share FabricColor's 33 colors; the command still permits explicit RGB for hair.

## Garment preset

Only `garments` is accepted at the top level. Slot IDs are `inner_top`, `top`, `outer`, `inner_bottom`, `bottom`, `legwear`, `feet`, `head`, `face`, `back`, `hands`, `neck`.

```json
{
  "garments": {
    "top": {
      "item": "createapparel:t_shirt",
      "material": "cotton",
      "primary_color": ["white", "cream"],
      "design": ["plain", "horizontal_stripe"],
      "secondary_color": ["navy"],
      "styles": {"tuck": ["untucked", "tucked"]}
    },
    "bottom": {
      "item": "createapparel:denim_pants",
      "material": "cotton",
      "primary_color": "navy"
    },
    "feet": {
      "item": "createapparel:sneakers",
      "material": "synthetic",
      "primary_color": "white"
    }
  }
}
```

Fabric entries require `item`, `material`, `primary_color`. `design`, `primary_color`, `secondary_color` and `accent_color` each accept a string or a nonempty array of unique strings. Optional `design` defaults to `plain`; absent secondary/accent fields remain absent. Present optional color arrays cannot be empty. All designs and colors are validated on reload, including candidates beyond the first. Choices are independent, so every declared combination should suit the outfit. Keep coordinated suit jackets and trousers in separately colored presets rather than independent navy/gray/black lists.

`item` and `material` remain single strings. `styles` is an optional category-to-value object whose values accept single strings or nonempty unique string lists. Each category is drawn independently and uniformly. All candidates must suit the garment definition, slot and full outfit, including the all-present optional outfit. For example, TOP rolled sleeves are incompatible with an OUTER layer, even if that layer is optional. Style names are garment/slot-specific: jackets use `open`, zip-up hoodies in OUTER use `opened`, shirt sleeves use `normal` rather than `unrolled`, and shirts in OUTER have no open/closed choice.

Each fabric or metal entry accepts `optional`, a strict boolean defaulting to `false`. `true` independently chooses whether to equip that slot, with a 50% chance, before its colors/styles are drawn. Omitted slots save no equipment or style. Present selected entries become fixed and do not reroll when resolved again. Optional clothes are still fully validated even when they could be omitted. Required entries alone must guarantee upper and lower coverage; an optional shirt/dress/pair of trousers cannot be the only necessary cover. Example: `"back": {"item":"createapparel:backpack", "material":"synthetic", "primary_color":["black","navy"], "optional":true}`.

Material support comes from the loaded SewingRecipe fabric tags and existing fabric attributes, rather than a second garment/material table. Items without a compatible sewing material are rejected. Designs use existing migration and validation. Validation does not expand a cartesian product of color/design/style lists. Conversion chooses concrete equipment, appearance and styles once; only resolved GarmentData and existing ApparelStyles are saved. Schema 1/2 remain compatible. Existing Citizens are not rerolled on reload or load.

For metal accessories, use `item`, `metal_color` and `gem`, plus optional `styles`, without fabric fields. For example, `neck: {"item":"createapparel:necklace","metal_color":"silver","gem":"diamond"}`. These values use existing MetalAccessoryData validation and NBT. A preset still needs upper and lower clothing alongside accessories.

Duplicate JSON keys/slots, unknown fields, invalid IDs, wrong JSON types, incompatible slot/material/design/style and unsupported colors reject the entire garment preset. No arbitrary NBT field is accepted. Upper coverage requires INNER_TOP or TOP; lower coverage requires INNER_BOTTOM or BOTTOM, or TOP clothing in the game-rule Item Tag `createapparel:citizen/lower_covering_tops`. Datagen classifies all 26 current dresses (A-line, straight, bodycon, fit-and-flare, camisole, jumper, shirt). This tag can be changed by a data pack and does not inspect rendering geometry. FEET, LEGWEAR, OUTER and accessories alone do not satisfy these conditions.

## Defaults and failures

The adult and child Body `createapparel:default` and Garment `createapparel:default` remain loaded and validated, but are fallback-only. Smart Phone conversion and Citizen egg/dispenser/spawner draws normally use the other presets. A usable normal Body/outfit pair prevents random default appearances, even if other bodies reference only default or missing outfits. Existing saved Citizens, ordinary summon and explicitly requested command `default` outfits are unchanged.

Shipped adult and child `default` bodies are classic/flat/natural/short with brown hair and eyes, and reference `createapparel:default`. Their age is supplied by the conversion. The compiled emergency appearance preserves Phase17's default RGB. Both use the old default clothing: white cotton T-shirt (TOP), blue cotton denim pants (BOTTOM), white synthetic sneakers (FEET). TOP and BOTTOM provide the two required coverage halves. CitizenOutfitPreset.DEFAULT and the compiled garment fallback share this definition; ordinary summon and the `default` command keep their previous outfit.

Malformed data emits warnings per ID and does not crash preset loading. The complete immutable catalog is replaced once after validation. If all bodies or references are invalid, the compiled fallback is used even when the same JSON files were overwritten. If garment definitions or sewing material support also make that fallback impossible, conversion aborts, the original Villager stays, and the player receives a short failure notification.

## Shipped everyday and work outfits

There are 10 adult Body presets, 8 child Body presets and 21 Garment presets. Names such as `male` and `female` organize the supplied body/outfit collections; they are not extra gender fields or equipment restrictions. Adult female bodies use slim/bust; child female bodies use slim/flat. The five adult female hairstyles reference casual, work and school outfits. Adult male short/centerpart/mash bodies also allow the male school uniform; the bald body retains its casual/work set. Children reference only casual outfits. Existing user-authored body palettes and the removal of adult `female_short` are retained.

New outfit palettes are curated for everyday, office and school clothing seen in contemporary Japanese settings. White/cream/light-blue tops, navy denim, beige trousers, restrained knitwear and matching navy/gray suits provide variation within coordinated combinations. Children have comfortable separates, hoodies and modest dress/skirt options. School uniforms are assigned to the adult-sized catalog, including blazer uniforms and female summer/winter sailor uniforms. Garment presets themselves contain no age or gender field; the supplied Body references determine which age group draws them.

The former `child_male_school_01` and `child_female_school_01` IDs are renamed to `male_school_01` and `female_school_01`, preserving their outfit contents. Update external data-pack references to those new IDs. Saved Citizens keep their resolved clothes and need no migration. Sailor uniforms use the existing blouse's secondary color for the collar/cuffs and accent color for the scarf; no extra necktie or outer layer obscures them.

| Garment ID | Outfit |
|---|---|
| `default` | Existing white T-shirt, blue jeans, white sneakers |
| `male_casual_01` | T-shirt, layered long-sleeve shirt with normal/rolled sleeves, jeans, canvas sneakers |
| `male_casual_02` | Pullover hoodie, slacks, sneakers |
| `male_casual_03` | Long-sleeve T-shirt, open zip-up hoodie, slacks, sneakers |
| `male_casual_04` | Long-sleeve T-shirt, open blazer, slacks, sneakers |
| `male_formal_01` | Black wool suit, tucked shirt, silk tie, dress shoes, optional backpack |
| `male_formal_02` | Navy wool suit, tucked shirt, silk tie, dress shoes, optional backpack |
| `male_formal_03` | Gray wool suit, tucked shirt, silk tie, dress shoes, optional backpack |
| `male_school_01` | Adult blazer/trousers, tucked shirt, tie, loafers, optional backpack |
| `female_casual_01` | Three-quarter-sleeve blouse, knee-length flared skirt, canvas sneakers |
| `female_casual_02` | V-neck T-shirt, optional open blazer, midi flared skirt, tights, knee-high boots |
| `female_casual_03` | Midi shirt dress, optional open denim jacket, canvas sneakers |
| `female_casual_04` | Short-sleeve camisole dress over slacks, pumps |
| `female_formal_01` | Round blouse, midi tight skirt, tights, pumps |
| `female_school_01` | Adult blazer/shirt, pleated skirt, socks, loafers, optional tie |
| `female_school_02` | Short-sleeve sailor blouse, pleated skirt, crew socks and loafers; user-edited palette lists |
| `female_school_03` | Long-sleeve sailor blouse, pleated skirt, crew socks and loafers; user-edited palette lists |
| `child_male_casual_01` | T-shirt, shorts, ankle socks, sneakers |
| `child_male_casual_02` | Pullover hoodie, jeans, ankle socks, sneakers, optional backpack |
| `child_female_casual_01` | T-shirt, optional short-sleeve outer shirt, shorts, knee socks, sneakers |
| `child_female_casual_02` | T-shirt, open zip-up hoodie, knee-length A-line skirt, knee socks, sneakers |

The user-authored formal preset originally referenced two unavailable item IDs: `createapparel:long_sleeves_shirt` was corrected to `createapparel:shirt_longsleeves`, and `createapparel:leather_shoes` to `createapparel:dress_shoes`. The five previously missing male casual/formal references now have definitions. All shipped hair, skin, dye, item, material, design, style, slot and outfit references are covered by unit and dedicated-server catalog validation.

## Lint and local preview

Run `.\\gradlew.bat test --tests com.rexhub.createapparel.citizen.CitizenPresetResourceLintTest` to lint every shipped JSON against strict schema/encoding, body references, garment IDs, materials, all designs/styles, slot restrictions and guaranteed coverage. Normal `build` includes this test; dedicated GameTests additionally validate actual registered items, loadouts, random optional draws and persistence.

The user-deleted female_formal_02/03 and child_male/female_casual_03 remain deleted; dangling Body references were removed, and new male_casual_04/female_casual_04 references added. Selected user palettes are retained. Incorrect item/design/style names were corrected to registered definitions, rather than introducing aliases.

The workspace-only preview helper at `run/phase18-preset-gallery/` creates a fresh world without overwriting earlier previews. Its default coverage mode exhibits every Body/outfit pair, every body color, each garment design with each declared color candidate, all style candidates, and optional presence/absence. It is not a full Cartesian enumeration, which exceeds tens of millions of combinations after the edits. An inventory guide book links to Body sections; a TSV records every shown configuration. Gallery code is excluded from the normal classes and distribution JAR.

## Citizen spawn egg

`createapparel:citizen_spawn_egg` (Citizen Spawn Egg / 市民のスポーンエッグ) is available in the mod's creative tab, or with `/give @s createapparel:citizen_spawn_egg`. Egg, dispenser and spawner creation use the same baby roll as Forge's vanilla Zombie: 5% by default, following the server `zombieBabyChance` setting. The selected age determines the Body catalog and allowed garment presets; the resolved appearance and clothes are saved without rerolling.

Using the egg on an existing Citizen creates a child, following vanilla spawn-egg interaction. Villagers use a group-spawn age rule, so their individual egg behavior is not identical to Zombies. This item does not add natural Citizen spawns or change Smart Phone conversion or command age selection.

## Phone model and verification

Datagen creates a real 7 × 13 × 1 model-pixel cuboid, x=4.5..11.5, y=1.5..14.5, z=7.5..8.5. The 16 × 16 PNG is a hand-edited asset at `src/main/resources/assets/createapparel/textures/item/smart_phone.png`; Datagen does not generate or overwrite it. Keep its dimensions and UV layout when editing: x=0..6/y=1..13 for the screen (SOUTH), x=8..14/y=1..13 for the back (NORTH), and x=15/y=1 for the frame. Model UV units match pixels in this 16 × 16 atlas: screen [0,1,7,14], back [8,1,15,14], frame [15,1,16,2]. All seven display transforms are generated; GUI tilts 10°/-15° to show the screen and thickness, fixed faces forward, ground lies flat, first-person tilts ±10°. Third-person uses zero rotation, translation [0,2,1] and scale 0.6 so its screen/back orientation is reversed from the initial model; first-person settings are retained.

Build/unit tests validate JSON, palettes, catalog selection and assets; dedicated GameTests cover real conversion, rollback, clothes, saved state and wire RGB. Actual first-/third-person hand orientation, left-hand mirroring, ground/frame appearance and player right-click/resource reload behavior require Minecraft visual verification.

## Smart Phone recipe

`createapparel:smart_phone` is produced by Create Mechanical Crafting with this 3 × 5 arrangement:

```text
GGG
TRT
CPC
TRT
BBB
```

G = glass pane ×3, T = electron tube ×4, R = redstone ×2, C = copper sheet ×2, P = precision mechanism ×1, B = brass sheet ×3. Output: one Smart Phone. The generated recipe is `data/createapparel/recipes/smart_phone.json`.

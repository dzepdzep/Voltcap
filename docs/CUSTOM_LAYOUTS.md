# Making a custom layout for Voltcap

A custom layout is a home screen you design yourself: where the games sit, how big the game's art is, what text
shows, and how all of it moves. You write it as one small text file (`layout.json`), add any pictures you want, and
pack them into a `.vclayout` file that anyone can import.

Nothing in a layout runs as code. It's a description that Voltcap draws, so a layout is safe to share.

Two finished examples are in this folder:

- `docs/layouts/neon-drift/layout.json`: a curved row along the bottom, the name and buttons on the left, the logo
  floating at the top right. This is the built-in example.
- `docs/layouts/side-list/layout.json`: a list of wide pictures down the left, the logo and info on the right.

---

## 1. Get started

1. On the handheld, go to **Settings › Home › Layout** and pick **Custom layout**.
2. Open **Layout options** and tap **Save the example**. Save `neon-drift.vclayout` somewhere you can reach.
3. A `.vclayout` is a zip file. Rename it to `.zip` (or open it with any zip tool) and take out `layout.json`.
4. Edit `layout.json` in any text editor (Notepad, VS Code, or a text editor on the handheld).
5. Put `layout.json` and your pictures side by side in a zip, and rename it to `my-layout.vclayout`.
6. Import it with **Layout options › Import a file**.

**Faster way to try changes:** make a folder `Voltcap/Layouts` on the handheld's internal storage. Put your layout
folder there (for example `Voltcap/Layouts/my-layout/layout.json` plus its pictures). Then tap
**Import from Voltcap/Layouts**. Tapping it again after each edit replaces the old copy, so you can edit and check in
a few seconds.

If something in `layout.json` is wrong, the import says what and where (for example `part 3: unknown type "titel"`).

---

## 2. The file

```json
{
  "format": 1,
  "name": "My layout",
  "author": "Your name",
  "background": { ... },
  "parts": [ { ... }, { ... } ]
}
```

| Field        | What it is                                                              |
|--------------|-------------------------------------------------------------------------|
| `format`     | Always `1` for now.                                                     |
| `name`       | Shown in the layout list. Importing a layout with the same name replaces it. |
| `author`     | Optional. Shown under the name.                                         |
| `background` | Optional. What's behind everything (section 5).                         |
| `parts`      | The things on screen, drawn in order: later parts go on top.            |

### Positions: shares of the screen

Every part has a box:

- `x` and `y` are where its **top-left corner** is.
- `w` and `h` are its **width** and **height**.

All four are shares of the layout's area, from `0` to `1`. So `"x": 0.5` is the middle, and `"w": 0.25` is a quarter
of the width. The area is the screen between the status bar and the button hints. A layout looks the same on every
screen size: on the Retroid Pocket Classic, the AYN Thor and a tablet.

```json
{ "type": "title", "x": 0.06, "y": 0.12, "w": 0.55, "h": 0.2 }
```

Every part can also have:

| Field    | Default | What it does                                            |
|----------|---------|---------------------------------------------------------|
| `alpha`  | `1`     | How solid it is, 0 (invisible) to 1.                    |
| `rotate` | `0`     | Turned by this many degrees.                            |
| `loop`   | none    | A motion that keeps going (section 4).                  |
| `enter`  | none    | A motion played when you move to another game (section 4). |

---

## 3. The parts

### `games`: the games you move through

Only one per layout. ◀ ▶ move along a row or an arc, ▲ ▼ along a column. Tap a game to pick it, and tap it again to
open it.

| Field       | Default | What it does |
|-------------|---------|--------------|
| `direction` | `row`   | `row` (left to right), `column` (top to bottom) or `arc` (a row that curves away from the middle). |
| `art`       | `cover` | `card` / `cover`: the card look picked in Settings (covers, cases, cartridges, discs, tiles…). `square`: covers cut square. `wide`: wide art. |
| `cardSize`  | `0.7`   | How big a game is across the row, as a share of the part's height (row) or width (column). |
| `focusSize` | `1`     | How big the game you're on is, on the same scale. Bigger than `cardSize` makes it stand out. |
| `gap`       | `0.02`  | Space between games, as a share of the part's length. |
| `anchor`    | `0.5`   | Where the game you're on stays, along the part: `0` is the start, `0.5` the middle, `1` the end. |
| `curve`     | `0.15`  | How much an `arc` bends (negative values bend upwards). |
| `corner`    | `12`    | Corner roundness in dp. |
| `glow`      | `true`  | The focus effect (glow / electric frame) on the game you're on. |
| `names`     | `false` | The name under the game you're on (rows and arcs). |
| `fadeEdges` | `false` | Games fade out near the part's ends. |

### `art`: the game you're on

| Field    | Default    | What it does |
|----------|------------|--------------|
| `art`    | `backdrop` | `backdrop` (the big background art), `cover`, `wide`, `logo`, `screenshot` or `card` (the card look). |
| `fit`    | `crop`     | `crop` fills the box, `fit` shows the whole picture, `stretch` fills it out of shape. Logos default to `fit`. |
| `corner` | `12`       | Corner roundness in dp. |

A game without a logo shows its name in the logo's place.

### `title`, `info`, `text`, `clock`, `category`: words

| Type       | Shows |
|------------|-------|
| `title`    | The game's name. |
| `info`     | A line made from `show`: any of `console`, `playtime`, `lastPlayed`, `achievements`, `category`, `position`, `count`. Empty items are left out. |
| `text`     | Your own `text`, with the words below filled in. |
| `clock`    | The time (or your own `text` with `{time}` / `{date}`). |
| `category` | The category you're in (All, Favorites, a console…). |

Words filled in anywhere in `text`: `{name}` `{console}` `{playtime}` `{lastPlayed}` `{achievements}` `{category}`
`{position}` (like "4 / 120") `{count}` (like "120 games") `{time}` `{date}`.

| Field      | Default   | What it does |
|------------|-----------|--------------|
| `size`     | `0.045` (title `0.07`) | Letter height, as a share of the screen's height. |
| `color`    | `#FFFFFF` | See colours (section 5). |
| `align`    | `start`   | `start`, `center` or `end`. |
| `bold`     | title only | `true` / `false`. |
| `italic`   | `false`   | |
| `font`     | `sans`    | `sans`, `serif`, `mono` or `cursive`. |
| `shadow`   | `true`    | A soft shadow so the words read over pictures. |
| `maxLines` | 2 (title), 3 | Longer text ends with "…". |

### `image`: your own picture

```json
{ "type": "image", "src": "frame.png", "x": 0, "y": 0, "w": 1, "h": 1, "fit": "stretch" }
```

`src` is a file in the zip, next to `layout.json`: PNG, JPG, WebP or GIF. **Animated GIF and WebP pictures play.**
`fit` and `corner` work as for `art`. Use transparent PNGs for frames, bezels and overlays.

### `shape`: a panel, a line or a dot

| Field         | Default | What it does |
|---------------|---------|--------------|
| `shape`       | `round` | `rect`, `round` (rounded corners) or `circle`. |
| `color`       | none    | One colour… |
| `gradient`    | none    | …or two or more, top to bottom. |
| `corner`      | `16`    | Corner roundness in dp (`round`). |
| `border`      | none    | Edge colour. |
| `borderWidth` | `0`     | Edge width in dp. |

### `buttons`: Play and Details

Two buttons side by side, filling the part. ▼ (▶ in a column layout) moves onto them. The first says **Continue**
for a game you've played, otherwise **Play**.

| Field       | Default   | What it does |
|-------------|-----------|--------------|
| `labels`    | Play / Continue, Details | Your own two labels. |
| `color`     | `accent`  | The Play button's colour (Details is a lighter shade of it). |
| `textColor` | `#FFFFFF` | |

---

## 4. Motion

### `loop`: keeps going

```json
"loop": { "kind": "float", "amount": 0.012, "seconds": 5 }
```

| `kind`   | What it does | `amount` (default) |
|----------|--------------|--------------------|
| `float`  | Bobs up and down. | Share of the height (0.012). |
| `sway`   | Moves left and right. | Share of the width (0.012). |
| `drift`  | Slides steadily across and back. | Share of the width (0.03). |
| `spin`   | Turns round and round. | Negative turns the other way. |
| `rock`   | Tilts back and forth. | Degrees (5). |
| `pulse`  | Grows and shrinks. | Share of its size (0.04). |
| `blink`  | Fades in and out. | How faint it gets, 0–1 (0.3). |
| `turn`   | Turns on its upright axis, like a card. | Degrees (18). |

`seconds` is the length of one round (default 4). `delay` (0–1) starts it part way round, so several parts can move
one after another instead of all together.

The background picture can have a loop too: `"background": { "image": "stars.png", "loop": { "kind": "drift" } }`.

### `enter`: plays when you move

```json
"enter": { "kind": "slide-right", "ms": 380, "delay": 0 }
```

`kind`: `fade`, `slide-left` (comes in from the left), `slide-right`, `slide-up` (comes up from below),
`slide-down`, `zoom`, `pop` (zooms in with a small bounce), `flip`, `spin` or `none`.

`ms` is how long it takes and `delay` how long it waits, both in milliseconds. Give the name a delay of 0, the info
100 and the logo 200, and they arrive one after another.

`on`: `move` plays it each time you move to another game (the default for game parts: art, title, info, text,
category). `start` plays it once when the layout appears (the default for games, images, shapes, the clock and the
buttons).

---

## 5. Background and colours

```json
"background": {
  "gradient": ["#0B0F1A", "#26123F"],
  "gameArt": true,
  "shade": 0.55,
  "image": "glow.webp",
  "fit": "crop"
}
```

Drawn from the back:

1. `color` or `gradient` (two or more colours, top to bottom).
2. `gameArt: true`: the game's background art, with `shade` (0–1) of black over it so the words read.
3. `image`: your picture on top of those, for example a frame with a transparent middle, or an animated GIF of
   rain, stars or scan lines.

Without a `background`, the game's art is shown.

**Colours** are `#RRGGBB` or `#AARRGGBB`, where `AA` is how solid it is (`00` clear, `80` half, `FF` solid). You
can also use `accent` (the theme's own colour, so the layout follows the user's theme), `text`, `white`, `black` or
`transparent`.

---

## 6. Tips

- **Start from an example** and change one thing at a time; importing from the `Voltcap/Layouts` folder is the quickest
  loop.
- **Mind the corners:** the status bar sits above the layout and the button hints below it, so the area is a little
  shorter than the screen.
- **Keep pictures small:** 1280 px wide is plenty for a full-screen picture on a handheld. Animated pictures cost the
  most, so use one big one rather than many. A layout can hold up to 80 files and 48 MB.
- **Words need a backing:** over bright art, add a `shape` with a dark `gradient` behind the text, or raise `shade`.
- **Test on both shapes:** a 4:3 screen (Retroid Pocket Classic) and a 16:9 one, since the positions are shares of
  the screen.
- **Original art only:** use pictures you made or may share. Don't use console makers' logos or trademarks.

## 7. Share it

Send the `.vclayout` file. To install it, people use **Import a file** or drop it into `Voltcap/Layouts`.

---

## Quick reference

```text
background: color | gradient[] | gameArt | shade | image | fit | loop
part:       type x y w h  alpha rotate loop enter
games:      direction(row|column|arc) art(card|cover|square|wide) cardSize focusSize gap anchor curve corner glow names fadeEdges
art:        art(backdrop|cover|wide|logo|screenshot|card) fit(crop|fit|stretch) corner
words:      title | info(show[]) | text(text) | clock | category  →  size color align bold italic font shadow maxLines
image:      src fit corner
shape:      shape(rect|round|circle) color | gradient[] corner border borderWidth
buttons:    labels[] color textColor
loop:       kind(float|sway|drift|spin|rock|pulse|blink|turn) amount seconds delay
enter:      kind(fade|slide-left|slide-right|slide-up|slide-down|zoom|pop|flip|spin|none) ms delay on(move|start)
words in text: {name} {console} {playtime} {lastPlayed} {achievements} {category} {position} {count} {time} {date}
```

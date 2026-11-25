# Bundled Profile Avatar & Background Sources & Licenses

All built-in profile avatars in Quern are bundled offline as `192×192` WebP drawables in `app/src/main/res/drawable-nodpi/av_<key>.webp` (45 files, ~245 KB total). All built-in profile backgrounds are drawn purely in code (zero bundled image assets). Nothing is downloaded at runtime unless the user explicitly enters a custom `https://` avatar or background image URL.

---

## 1. Quern Glass Medallion, Serif Monogram & Light-Painting Backgrounds
- **Author**: Quern (original code-drawn Compose Canvas / typography)
- **License**: MIT License
- **Usage**: Dark obsidian glass medallion frame, per-avatar colored inner radial glow, specular glass highlight, luminous rim, profile colour ring, serif Monogram renderer (12 glass tints), and 9 code-drawn light-painting profile backgrounds.

---

## 2. Microsoft Fluent Emoji 3D (30 avatars — Animals & Characters)
- **Source**: [microsoft/fluentui-emoji](https://github.com/microsoft/fluentui-emoji)
- **License**: MIT License
- **Processing**: Downloaded offline from `main` branch (`3D/*.png`), resized to `192×192` with Lanczos resampling, and encoded as transparent WebP (`quality=84`).

### Full MIT License Notice (Microsoft Fluent Emoji)

```text
MIT License

Copyright (c) Microsoft Corporation.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### Animals (16)
| Key | Drawable | Upstream Path (`microsoft/fluentui-emoji`) |
| --- | --- | --- |
| `fox` | `av_fox.webp` | `assets/Fox/3D/fox_3d.png` |
| `cat` | `av_cat.webp` | `assets/Cat face/3D/cat_face_3d.png` |
| `dog` | `av_dog.webp` | `assets/Dog face/3D/dog_face_3d.png` |
| `lion` | `av_lion.webp` | `assets/Lion/3D/lion_3d.png` |
| `tiger` | `av_tiger.webp` | `assets/Tiger face/3D/tiger_face_3d.png` |
| `panda` | `av_panda.webp` | `assets/Panda/3D/panda_3d.png` |
| `koala` | `av_koala.webp` | `assets/Koala/3D/koala_3d.png` |
| `bear` | `av_bear.webp` | `assets/Bear/3D/bear_3d.png` |
| `wolf` | `av_wolf.webp` | `assets/Wolf/3D/wolf_3d.png` |
| `rabbit` | `av_rabbit.webp` | `assets/Rabbit face/3D/rabbit_face_3d.png` |
| `owl` | `av_owl.webp` | `assets/Owl/3D/owl_3d.png` |
| `penguin` | `av_penguin.webp` | `assets/Penguin/3D/penguin_3d.png` |
| `dragon` | `av_dragon.webp` | `assets/Dragon face/3D/dragon_face_3d.png` |
| `unicorn` | `av_unicorn.webp` | `assets/Unicorn/3D/unicorn_3d.png` |
| `octopus` | `av_octopus.webp` | `assets/Octopus/3D/octopus_3d.png` |
| `frog` | `av_frog.webp` | `assets/Frog/3D/frog_3d.png` |

### Characters (14)
| Key | Drawable | Upstream Path (`microsoft/fluentui-emoji`) |
| --- | --- | --- |
| `astronaut` | `av_astronaut.webp` | `assets/Woman astronaut/Medium-Light/3D/woman_astronaut_3d_medium-light.png` |
| `robot` | `av_robot.webp` | `assets/Robot/3D/robot_3d.png` |
| `mage` | `av_mage.webp` | `assets/Man mage/Medium-Dark/3D/man_mage_3d_medium-dark.png` |
| `ninja` | `av_ninja.webp` | `assets/Ninja/Medium/3D/ninja_3d_medium.png` |
| `superhero` | `av_superhero.webp` | `assets/Woman superhero/Dark/3D/woman_superhero_3d_dark.png` |
| `vampire` | `av_vampire.webp` | `assets/Man vampire/Light/3D/man_vampire_3d_light.png` |
| `genie` | `av_genie.webp` | `assets/Woman genie/3D/woman_genie_3d.png` |
| `elf` | `av_elf.webp` | `assets/Woman elf/Medium-Light/3D/woman_elf_3d_medium-light.png` |
| `detective` | `av_detective.webp` | `assets/Man detective/Medium/3D/man_detective_3d_medium.png` |
| `cook` | `av_cook.webp` | `assets/Woman cook/Medium-Dark/3D/woman_cook_3d_medium-dark.png` |
| `scientist` | `av_scientist.webp` | `assets/Man scientist/Light/3D/man_scientist_3d_light.png` |
| `artist` | `av_artist.webp` | `assets/Woman artist/Dark/3D/woman_artist_3d_dark.png` |
| `alien` | `av_alien.webp` | `assets/Alien/3D/alien_3d.png` |
| `ghost` | `av_ghost.webp` | `assets/Ghost/3D/ghost_3d.png` |

---

## 3. DiceBear Lorelei (15 avatars — People, full colour)
- **Source**: [DiceBear](https://www.dicebear.com/) (`@dicebear/core` + `@dicebear/collection`, style **Lorelei** by Lisa Wischofsky)
- **License**: CC0 1.0 Universal (Public Domain Dedication — no attribution required, commercial use permitted). DiceBear generation tooling is licensed under MIT.
- **Processing**: Generated offline outside the repository from fixed seeds and deterministic full-colour trait options, rasterized to `192×192` transparent PNGs via `@resvg/resvg-js`, and encoded as transparent WebP (`quality=84`).

| Key | Drawable | Fixed Seed | Notable Traits |
| --- | --- | --- | --- |
| `person_nora` | `av_person_nora.webp` | `quern-lorelei-nora` | Warm light skin, chestnut hair, earrings |
| `person_tariq` | `av_person_tariq.webp` | `quern-lorelei-tariq` | Medium-tan skin, dark hair, beard, glasses |
| `person_layla` | `av_person_layla.webp` | `quern-lorelei-layla` | Olive skin, auburn-brown hair, flower accessory |
| `person_amara` | `av_person_amara.webp` | `quern-lorelei-amara` | Deep brown skin, dark hair, gold glasses |
| `person_clara` | `av_person_clara.webp` | `quern-lorelei-clara` | Fair skin, copper-red hair, freckles |
| `person_kenji` | `av_person_kenji.webp` | `quern-lorelei-kenji` | Golden skin, slate hair, glasses |
| `person_samira` | `av_person_samira.webp` | `quern-lorelei-samira` | Caramel skin, plum hair, gold earrings |
| `person_zayd` | `av_person_zayd.webp` | `quern-lorelei-zayd` | Rich brown skin, dark hair, beard |
| `person_hana` | `av_person_hana.webp` | `quern-lorelei-hana` | Light warm skin, navy hair, sky-blue accessory & earrings |
| `person_karim` | `av_person_karim.webp` | `quern-lorelei-karim` | Tan skin, espresso hair, beard |
| `person_maya` | `av_person_maya.webp` | `quern-lorelei-maya` | Deep brown skin, berry-wine hair, rose glasses & earrings |
| `person_adam` | `av_person_adam.webp` | `quern-lorelei-adam` | Peach skin, slate hair, blue-tinted glasses, beard |
| `person_yara` | `av_person_yara.webp` | `quern-lorelei-yara` | Warm bronze skin, deep violet-black hair, lilac accessory |
| `person_leo` | `av_person_leo.webp` | `quern-lorelei-leo` | Fair skin, honey-blonde hair, freckles |
| `person_sara` | `av_person_sara.webp` | `quern-lorelei-sara` | Caramel skin, dark brown hair, amber glasses & earrings |

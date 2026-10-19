# CraftRoute

AE2's automatic crafting calculation gets it wrong sometimes. This mod does two things about it:

1. It replaces AE2's recursive calculation with a sequential stack VM: faster, deep chains actually finish, and the missing-ingredient list is more trustworthy.
2. It adds a crafting tree canvas. The path is drawn out, and when you disagree with it you pick the recipe for each node yourself and encode the result into patterns.

Chinese: [README.md](README.md) · Third-party notices: [ATTRIBUTIONS.md](ATTRIBUTIONS.md)

---

## What is in this repo

- **Crafting engine**: patterns are compiled to bytecode at encode time and executed sequentially; subtree caching, BigInteger quantities, no overflow.
- **Cycles and self-growing recipes**: dead loops are pruned while picking recipes, seeded loops keep their members and consume real stock, pure conversion rings are checked by value conservation.
- **Crafting tree canvas**: F8 in any AE2 terminal, native JEI recipes on the right, per-node recipe choice, drag / zoom / undo / delete, paged search.
- **Pattern workbench**: encode into an 18-slot cache (player NBT), move to inventory, upload straight to pattern providers on the same grid.
- **Integrations**: ExtendedAE / AdvancedAE / AE2 Lightning Tech / Mekanism (Applied Mekanistics) / WCWT terminal / FTB Library / Dark Mode Everywhere.

---

## Why this exists

AE2 calculates a crafting path by walking the pattern tree recursively. That is fine until you hit a modpack with long chains, and then:

- **The counts are off.** The network has the items but it reports `missing XX x99`. Or the plan says feasible, you submit it, and the CPU sits at zero progress until you cancel and retry.
- **Deep chains do not finish.** Tens of levels turn hundreds of milliseconds into tens of seconds, and at large quantities it stops returning at all and blocks the server thread.
- **Loops are mishandled.** Self-growing recipes like `A + B -> 2A`, or pure conversion rings like `9B -> A / 1A -> 9B`, get treated as dead loops or produce a plan that cannot deliver.
- **You cannot see the path.** You get a number. You cannot see which recipe it picked, or what else an intermediate could be made from.

The first three are calculation problems. The fourth is an observability problem. This mod addresses both.

---

## 1. Calculation: recursion replaced by a VM

Active as soon as the mod is installed. AE2's crafting calculation is intercepted: patterns are compiled to bytecode at **encode** time, and a request executes that bytecode sequentially instead of recursing. Sub-patterns resolve lazily, already-computed subtrees are cached, and quantities use BigInteger so intermediate values cannot overflow.

The only acceptance criterion is that **results match vanilla AE2**: the same plan when it is computable, the same missing list when it is not, otherwise the request falls back to the original algorithm.

To disable it: `proxyEnabled = false` in `config/craftroute-common.toml` (restart required; editable in-game with Configured).

Rough numbers: a ten-level chain costs tens of milliseconds cold; recomputing the same request costs 100-200ns at the median (warm hit). These come from the benchmark suite in the repo, not from estimates.

---

## 2. Canvas: build the tree by hand

Press `F8` in any AE2 terminal, or click the canvas button in the terminal toolbar.

Opened from the crafting confirmation screen, it imports the tree you are already calculating (final output and amount) and starts in edit mode. Opened from a terminal, it starts empty.

**Using it**

- Click a node and the recipe shows up on the right: the real JEI recipe screen, not a redrawn copy.
- Pick a recipe and its ingredients appear as child nodes; each ingredient can be the output of the next level down, until you reach raw materials or loop back.
- Drag nodes, pan the canvas, `Ctrl+wheel` to zoom, `Home` to fit, `Ctrl+Z` to undo, `Delete` to remove the selected node.
- Right-click a node (or click `+`) for the recipe menu: switch recipe, clear only this branch, or remove this node and everything under it. The root is removable too; that returns the canvas to the empty state and can be undone.

**Search**

The search box matches Chinese names and item IDs, six results per page, wheel or arrows to page. Results can be dragged onto a node.

**Encoding**

- Encoding consumes one blank pattern from the network. The finished pattern goes into your pattern cache (18 slots, stored in player NBT, survives death).
- "Move to inventory" only moves what fits; a full inventory drops nothing.
- "Upload to provider" lists pattern providers on the same grid and dimension, with used and total slots. After the drop the server re-validates menu, grid, provider, slot and token; stale drags and attempts to overwrite existing patterns are rejected.

**Looks**

Colours are sampled from the AE2 terminal (panel `#CBCCD4`, workspace `#9A9FB4`, slot border `#696D88`). Buttons are custom-drawn but only override rendering; clicks still go through vanilla `Button`, so global recolouring mods like Dark Mode Everywhere cannot black them out.

> One practical note: the screen is a plain `Screen`, not a container screen. As a container screen JEI stops drawing its item list on the right, and FTB and Dark Mode inject their own buttons into it.

---

## Install

1. Minecraft 1.21.1 + NeoForge 21.1.169+
2. Applied Energistics 2 19.2.17+ (required)
3. JEI 19.57+ (optional, needed for the recipe panel)
4. Drop `craftroute-1.13.19-local.22-dev_neoforge_1.21.1.jar` into `mods/`, removing older versions first

Client and server must run the same version: the packet channel is `route-2`.

---

## Building from source

JDK 21 required. Dependency paths default to the instance `mods` directory (`../mods` relative to the project root); override them with Gradle properties elsewhere:

```bat
gradlew.bat -PjeiJar=<jei.jar> -Pae2ctJar=<ae2ct.jar> -PthunderboltJar=<thunderbolt.jar> jar
```

Output lands in `build/libs/`. Copying into an instance is a separate task and no longer runs as part of `build`:

```bat
gradlew.bat -PmodsDir=<instance mods dir> copyJarToMods
```

---

## Known limitations

- **The canvas project is not saved.** Close the canvas and your tree is gone. The pattern cache persists, the tree does not.
- **The solver was not rewritten.** Server-side demand is still applied uniformly per material; per-node recipe choices on the canvas do not mean a CPU can run the same item through two different branches.
- **Auto-import takes the first recipe only.** The AE2CT snapshot is matched by output, so a second recipe for the same output is ignored.
- **Provider upload is narrow.** Only providers exposing `PatternContainer` + `InternalInventory` are supported; there is no drag-back, swap, or overwrite of existing patterns.
- **Complex recipes are only correct visually.** A JEI layout is not proof that the pattern semantics are right; probabilistic outputs, non-consumed catalysts, machine config and energy still need per-case work.
- **Limits**: 80 nodes and 24 levels on the canvas; 256 search results, 6 per page.
- **The dark-mode button offset targets the WCWT terminal only.** Other terminal mods need their own check.

---

## Third-party mods

None of this would run without other people's work: the AE2 API, JEI's recipe rendering, AE2CT's tree data, and a pile of modpack integrations.

**What is referenced, under which license, and whether it is bundled is documented in [ATTRIBUTIONS.md](ATTRIBUTIONS.md)**, including every Mixin target class. Short version: neither the repo nor the JAR contains any third-party mod code or assets; all dependencies are `compileOnly` and installed by the player.

AE2:Crafting Tree is All Rights Reserved. This project only reads data it has already synchronised to the client, plus one compatibility Mixin that moves a button; no AE2CT code is copied. It will be removed on the author's request.

## License

LGPL-3.0, see [LICENSE](LICENSE). Rights in third-party works stay with their authors.

## Repositories

- Main: https://github.com/Hsunxl/CraftRoute
- History and other MC version branches (1.7.10 to 26.1.2): https://github.com/Hsunxl/AE2-VM

# Contributing to Yarn

Yarn is in a slightly precarious position, due to the odd nature of MCP. MCP is all-rights-reserved, yet visible to anyone
who has ever modded Minecraft using anything other than Fabric. This leads to "contamination" of a lot of potential
contributors, since people already have a conception of what a method "should" be named. Because of this, we have decided to
take a clean-room approach with mapping Minecraft to avoid potential legal/drama issues within the community.

## What's in a Name?

The fundamental goal of mappings is to make modding Minecraft easier to understand, for beginners and experts alike. We want
to create a cohesive, simple naming scheme that avoids confusion or being too verbose. Having a lot of things named in
different schemes would be a nightmare for usability, so we want to avoid that. This includes naming schemes not just within
Yarn but within the greater Minecraft community. Despite that goal, there are some name sources that are inherently more
"reliable" than others; you should use an up-to-date and known Mojang name above any alternative, even if an alternative
would be simpler.

## Guidelines

To keep with the goal of consistency within the mappings and the greater Minecraft community, here are a set of guidelines to
follow when naming unmapped methods.

### 1. Use modern, sensible Mojang names when known.

This can be a contentious decision, due to many of Notch's original names for things (that he himself has stated were
terrible choices for names) sticking around. While it would be nice to have names like `Gui` or the like, we have decided to
use `Screen`, since that's the name for guis that the client-side registry uses. Mojang names can be found in many places,
from registry names to lang files to tweets by devs. If you want to claim something as a Mojang name, and it's not an obvious
one, provide a source for proof.

Minecraft is still under active development, and names have been changed in the past. The most notable case of this is `Tile`,
which used to be the name for Blocks. This has changed in 1.13 and 1.14, so we use the modern name `BlockEntity` instead of
the old Notch name `TileEntity`.

There are a few situations when we've decided that Mojang's names are too obtuse or clunky for our mappings, where we've
decided to use our own names. The most obvious example is our use of `Identifier` when Mojang uses `NamespacedString`. In
these cases, we should make a community consensus on whether to use a better name, and what the name should be.

### 2. Avoid MCP and other proprietary names wherever possible.

Due to the reasons stated above, we need to avoid using the same names as MCP for classes where we can avoid it. This can
often clash with the first rule, since MCP used many Mojang names as well. In these cases, keep with the Mojang name, since
it's not something that's original to MCP. We don't need to change the name of `ItemStack`, for example.

### 3. Use a natural (English) word order.

As a group, we've decided to focus on a more natural word order for our mappings. The most notable example of this in our
mappings is that we use a *suffix* system instead of a *prefix* system. That would mean using something like `LoomBlock ` and
`LoomBlockEntity` instead of `BlockLoom` and `BlockEntityLoom`. This should be applied to methods and fields as well as
classes.

### 4. Make names explicit and efficient.

When someone calls a method, they should know exactly what it will do and exactly what will result from it. While this can be
found out by looking at the method code, it's a much better experience for a dev to be able to find out by seeing the name.
Instead of using terms like `serialize` and `deserialize`, use something like `toJson` and `fromJson` or `toNbt` and
`fromNbt`, depending on what the result of the method is. Methods should only have matching names if they do the same thing
with the same structures.

Despite this, there's such thing as a name that's *too* descriptive. Excessively long class or method names can be a major
annoyance, and a hindrance to being able to program as well. We've recently had large amounts of discussion of the name
scheme for packets, trying to find the best balance between descriptiveness and efficiency. We have, for the time being,
settled on a name scheme that involves abbreviations. Abbreviations should be avoided when possible, but when you have
classes with names like `ScoreboardObjectiveUpdateS2CPacket`, the abbreviations are necessary to prevent the name being
even more excessively long.

### 5. Don't be afraid to dispute existing names.

The Fabric community is active and evolving. Because of this, what's agreed upon as a good name can change over time. If you
have an issue with a name and don't feel like it fits in Yarn, don't hesitate to make an issue about it. We're still having
active discussion about packet naming schemes at the time of writing, and we may choose to change the scheme in the future if
enough people think a change is warranted. We want to avoid a LexManos situation where one person has to rule with an iron
fist, but that requires an active community who won't stay quiet for fear of being banned from the community because of their
PR. Healthy discussion is the best thing that Yarn can have.

# Contributing to Yarn

## What's in a Name?

The fundamental goal of mappings is to make modding Minecraft easier to understand, for beginners and experts alike. We want
to create a cohesive, simple naming scheme that avoids confusion or being too verbose. Having a lot of things named in
different schemes would be a nightmare for usability, so we want to avoid that. This includes naming schemes not just within
Yarn but within the greater Minecraft community. Despite that goal, there are some name sources that are inherently more
"reliable" than others; if an official term is hinted at or can be guessed, you should generally use that - sticking closer to 
official terminology helps everyone.

## Guidelines

To keep with the goal of consistency within the mappings and the greater Minecraft community, here are a set of guidelines to
follow when naming unmapped methods.

### 1. When known, stick to official terminology.

This can be a contentious decision, due to many of Notch's original names for things (that he himself has stated were
terrible choices for names) sticking around. While it could be nice to have names like `Gui` or the like, we have decided to
use `Screen`, since that's the name for guis that the client-side registry uses. Official terms can be found in many places,
from registry names through lang files to tweets by devs. In addition, we are capable of deriving some names - like enum type
names or registry object field names - automatically from the binaries. Those should usually be left untouched.

Minecraft is still under active development, and names have been changed in the past. The most notable case of this is `Tile`,
which used to be the name for Blocks. This has changed at some point, so we use the modern name `BlockEntity` instead of
the old name `TileEntity`. In general, don't fear updating our custom mappings to official terminology when new information
becomes available.

There are a few situations when we've decided that official names are too obtuse or clunky for our mappings, where we've
decided to use our own names. The most obvious example is our use of `Identifier` when the official name is assumed to be
`NamespacedString`. In these cases, we should make a community consensus on whether to use a better name, and what the name
should be.

### 2. Do not rip off other mapping projects.

Other mapping projects are the result of other people's hard work. Coming up with names is not quite trivial, and people can
get upset if someone just takes them and runs with them. Essentially - don't open up a copy of the game decompiled with another
mapping set "just to check how something works" or "just to check how they named it so I can come up with my own name" - that
is not the right thing to do! (Of course, some names are derived from official terminology - those are likely to be identical
across all projects.)

### 3. Stick to Java coding conventions.

Mappings should, of course, stick to the [standard Java naming conventions](https://www.oracle.com/technetwork/java/javase/documentation/codeconventions-135099.html#367).
Packages should be all lower case, class/enum/interface names should be `TitleCase`, fields and methods should be `camelCase`,
and constants should be `UPPER_CASE`.

### 4. Use a natural (English) word order.

As a group, we've decided to focus on a more natural word order for our mappings. The most notable example of this in our
mappings is that we use a *suffix* system instead of a *prefix* system. That would mean using something like `LoomBlock` and
`LoomBlockEntity` instead of `BlockLoom` and `BlockEntityLoom`. This should be applied to methods and fields as well as
classes.

In keeping with the natural English feel, we prefer to avoid prefixing interfaces and enums with `I` and `Enum` respectively.
While they can be useful for differentiation, we feel that in most cases there should be enough room to differentiate
classes without requiring prefixes.

### 5. Make names explicit and efficient.

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

As of writing, there is not yet a way to add comments to classes, fields or methods. However, we hope to eventually add it
to move a bit of the burden off names, as well as give them their sometimes much-needed context.

### 6. Don't be afraid to dispute existing names.

The Fabric community is active and evolving. Because of this, what's agreed upon as a good name can change over time. If you
have an issue with a name and don't feel like it fits in Yarn, don't hesitate to make an issue about it. We're still having
active discussion about packet naming schemes at the time of writing (and quite a few other open issues without clear agreement),
and we may choose to change the scheme in the future if enough people think a change is warranted. We want to avoid progress being
held up by fear or habit, even if the result is beneficial in the end. Healthy discussion is the best thing that Yarn - and the
Fabric project as a whole, can have.

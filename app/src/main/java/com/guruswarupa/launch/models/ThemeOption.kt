package com.guruswarupa.launch.models

data class ThemeOption(
    val id: String,
    val name: String,
    val wallpaperUrl: String,
    val category: String,
    val isDark: Boolean = true
) {
    companion object {
        val PREDEFINED_THEMES = listOf(

            ThemeOption(
                "stardust",
                "Deep Stardust",
                "https://i.pinimg.com/1200x/0e/dd/70/0edd7082a53fb8ad4cfe4d361904a0a1.jpg",
                "Landscape"
            ),
            ThemeOption(
                "forest",
                "Evergreen Forest",
                "https://i.pinimg.com/1200x/f4/7d/d5/f47dd53023bf5efbdf2968ae145e30f8.jpg",
                "Landscape"
            ),
            ThemeOption(
                "midnight_peak",
                "Midnight Peak",
                "https://i.pinimg.com/1200x/35/4b/76/354b76938cdf21f8b6e09944e46c3186.jpg",
                "Landscape"
            ),
            ThemeOption(
                "desert",
                "Golden Sands",
                "https://i.pinimg.com/1200x/1a/77/dc/1a77dcc2b9b830d835d5ce97670fd893.jpg",
                "Landscape"
            ),
            ThemeOption(
                "aurora",
                "Aurora Borealis",
                "https://i.pinimg.com/1200x/24/6d/30/246d300272e182a154a400f15618777e.jpg",
                "Landscape"
            ),
            ThemeOption(
                "ocean",
                "Pacific Blue",
                "https://i.pinimg.com/736x/2c/90/d7/2c90d74fed30e3563155d3158a23d856.jpg",
                "Landscape"
            ),
            ThemeOption(
                "autumn",
                "Autumn Ember",
                "https://i.pinimg.com/736x/db/6f/31/db6f310af1b34a66af83037c45651827.jpg",
                "Landscape"
            ),
            ThemeOption(
                "misty_mountains",
                "Misty Mountains",
                "https://i.pinimg.com/736x/81/6e/e8/816ee854fde214b79d1cd55765af3ffd.jpg",
                "Landscape"
            ),
            ThemeOption(
                "canyon_glow",
                "Canyon Glow",
                "https://i.pinimg.com/736x/52/ad/27/52ad271bc262700a52108d8a53035bda.jpg",
                "Landscape"
            ),

            ThemeOption(
                "cyber",
                "Cyber City",
                "https://i.pinimg.com/1200x/cf/7f/a9/cf7fa9350a8d2a21ee70069059fa641c.jpg",
                "City"
            ),
            ThemeOption(
                "tokyo_night",
                "Tokyo Neon",
                "https://i.pinimg.com/736x/03/0a/74/030a74346c9324c13e7939b7c6873f70.jpg",
                "City"
            ),
            ThemeOption(
                "ny_skyline",
                "Gotham Spirit",
                "https://i.pinimg.com/1200x/a5/9a/2d/a59a2d7341995e943ea455b68a3b8cdc.jpg",
                "City"
            ),
            ThemeOption(
                "blr_blooms",
                "Bengaluru Blooms",
                "https://i.pinimg.com/736x/47/c1/ab/47c1abb68c056f4ba1d96d3a87512322.jpg",
                "City"
            ),
            ThemeOption(
                "paris_rain",
                "Paris Rain",
                "https://i.pinimg.com/736x/1d/a2/cf/1da2cf9466f40336862a5b553524b5e6.jpg",
                "City"
            ),

            ThemeOption(
                "lavender",
                "Lavender Mist",
                "https://i.pinimg.com/736x/9a/02/0c/9a020c30f610d2930852e9530b153c7a.jpg",
                "Abstract"
            ),
            ThemeOption(
                "solar",
                "Solar Flare",
                "https://i.pinimg.com/736x/c4/9a/d4/c49ad42724b3b8095c3289af5513d45f.jpg",
                "Abstract"
            ),
            ThemeOption(
                "crimson",
                "Crimson Dusk",
                "https://i.pinimg.com/1200x/3d/6f/89/3d6f89f30253f975787b90ed52a6ff74.jpg",
                "Abstract"
            ),
            ThemeOption(
                "neon_waves",
                "Neon Waves",
                "https://i.pinimg.com/1200x/67/9d/06/679d0661b90e13ab211aa81f33f470b2.jpg",
                "Abstract"
            ),
            ThemeOption(
                "ethereal",
                "Ethereal Dream",
                "https://i.pinimg.com/1200x/8c/b3/83/8cb3832b4848ee028bb9224a433b5279.jpg",
                "Abstract"
            ),

            ThemeOption(
                "nordic",
                "Nordic Ice",
                "https://i.pinimg.com/736x/8e/ca/6f/8eca6fe01ef8e6d6f43b1f4cbd628196.jpg",
                "Minimal"
            ),
            ThemeOption(
                "volcanic",
                "Volcanic Ash",
                "https://i.pinimg.com/1200x/a5/b0/1a/a5b01a17de8e3a8e795f0cf6416f842d.jpg",
                "Minimal"
            ),
            ThemeOption(
                "minimalist",
                "Minimal Slate",
                "https://i.pinimg.com/1200x/b1/05/65/b105650530a4584e120758f032026a78.jpg",
                "Minimal"
            ),
            ThemeOption(
                "pure_dark",
                "Pure Obsidian",
                "https://i.pinimg.com/1200x/7c/21/6c/7c216c0814272b3dde71a2194e4c914e.jpg",
                "Minimal"
            ),
            ThemeOption(
                "arctic_white",
                "Arctic White",
                "https://i.pinimg.com/1200x/f6/a6/38/f6a638926c93f430abd696686b2e07d6.jpg",
                "Minimal",
                isDark = false
            ),

            ThemeOption(
                "nebula",
                "Cosmic Nebula",
                "https://i.pinimg.com/1200x/7b/45/77/7b4577c64009cb0f816c3e0ef7fee0e1.jpg",
                "Space"
            ),
            ThemeOption(
                "supernova",
                "Supernova Bloom",
                "https://i.pinimg.com/736x/85/bf/c9/85bfc9aa8040843da51b79e9363fc55c.jpg",
                "Space"
            ),
            ThemeOption(
                "void",
                "The Void",
                "https://i.pinimg.com/1200x/ab/64/74/ab6474f03f36e309c114bb954dddedf0.jpg",
                "Space"
            ),

            ThemeOption(
                "pixel_sunset",
                "8-Bit Sunset",
                "https://i.pinimg.com/736x/f1/a2/57/f1a257d35a3cff7b2f983821d78d4104.jpg",
                "Retro"
            ),
            ThemeOption(
                "vaporwave",
                "Vaporwave Ride",
                "https://i.pinimg.com/1200x/47/03/3e/47033e39544f7932257de9975a787d82.jpg",
                "Retro"
            ),
            ThemeOption(
                "classic_arcade",
                "Arcade Spirit",
                "https://i.pinimg.com/1200x/49/6b/3a/496b3ae2cc2ce5769c1116724a89d4a5.jpg",
                "Retro"
            ),

            ThemeOption(
                "monstera",
                "Monstera Leaf",
                "https://i.pinimg.com/736x/c9/88/71/c98871d8b59f841bf8f087a5a31e8850.jpg",
                "Nature"
            ),
            ThemeOption(
                "cherry_blossom",
                "Sakura Bloom",
                "https://i.pinimg.com/1200x/a7/07/92/a707925633db413cd7d514767ecdfa1f.jpg",
                "Nature"
            ),
            ThemeOption(
                "autumn_leaf",
                "Fallen Leaves",
                "https://i.pinimg.com/736x/54/19/c5/5419c5bff1bdb5367e873ab20f47f269.jpg",
                "Nature"
            ),

            ThemeOption(
                "eiffel",
                "Eiffel Tower",
                "https://i.pinimg.com/1200x/73/09/f4/7309f42ac228bd35ecd2526279a40d8e.jpg",
                "Architecture"
            ),
            ThemeOption(
                "taj_mahal",
                "Taj Mahal",
                "https://i.pinimg.com/736x/d5/4f/38/d54f38cf6b418934aea87c684aad23ef.jpg",
                "Architecture"
            ),
            ThemeOption(
                "ancient_egypt",
                "Egypt",
                "https://i.pinimg.com/736x/3a/93/b3/3a93b3ebd14b790b271faee4f7d9f29d.jpg",
                "Architecture"
            ),
            ThemeOption(
                "great_wall_of_china",
                "Great Wall of China",
                "https://i.pinimg.com/736x/ed/e5/b6/ede5b6830ec6b8d46eea11566f80901e.jpg",
                "Architecture"
            ),

            ThemeOption(
                "mercury",
                "Mercury",
                "https://i.pinimg.com/736x/18/1b/90/181b90c92743f9441f9dfdf286efd1c7.jpg",
                "Planets"
            ),
            ThemeOption(
                "venus",
                "Venus",
                "https://i.pinimg.com/1200x/ce/d5/a4/ced5a43cb473c347ac93757d3247a693.jpg",
                "Planets"
            ),
            ThemeOption(
                "earth",
                "Earth",
                "https://i.pinimg.com/1200x/17/69/53/176953775f23a549170303647d4fe7bb.jpg",
                "Planets"
            ),
            ThemeOption(
                "mars",
                "Mars",
                "https://i.pinimg.com/736x/84/fd/21/84fd215e6beebaa32f7421067c6c8481.jpg",
                "Planets"
            ),
            ThemeOption(
                "jupiter",
                "Jupiter",
                "https://i.pinimg.com/1200x/ae/9b/61/ae9b61efcf0367eaf98222dbf5b5c30e.jpg",
                "Planets"
            ),
            ThemeOption(
                "saturn",
                "Saturn",
                "https://i.pinimg.com/1200x/a0/a1/6f/a0a16fbb86a1eba87c21f3fd8d8cfbe0.jpg",
                "Planets"
            ),
            ThemeOption(
                "uranus",
                "Uranus",
                "https://i.pinimg.com/736x/2f/b3/6c/2fb36cfca622c7ff570f528fa9014314.jpg",
                "Planets"
            ),
            ThemeOption(
                "neptune",
                "Neptune",
                "https://i.pinimg.com/1200x/31/44/d4/3144d406e92b9cce12d24fcec69e2f3a.jpg",
                "Planets"
            )
        )
    }
}

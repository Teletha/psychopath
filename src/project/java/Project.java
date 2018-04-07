/*
 * Copyright (C) 2018 Psychopath Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
public class Project extends bee.api.Project {

    {
        product("com.github.teletha", "Psychopath", "0.6");

        require("com.github.teletha", "sinobu", "1.0");
        require("com.github.teletha", "antibug", "0.6").atTest();
    }
}

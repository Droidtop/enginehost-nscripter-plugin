/* -*- C++ -*-
 *
 *  onscripter_options.h -- ONScripter's command-line options
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 */

#ifndef __ONSCRIPTER_OPTIONS_H__
#define __ONSCRIPTER_OPTIONS_H__

void optionHelp();
void optionVersion();
// Applies argv-style options (without the program name) to the global ons.
void parseOption(int argc, char *argv[]);

#endif

#!/usr/bin/perl
# artwork-driver.pl — minimal host for the QuickNotch artwork helper.
#
# macOS only answers MediaRemote artwork queries to processes whose bundle
# identity starts with "com.apple.". `/usr/bin/perl` carries such an identity,
# so this script loads our tiny `artwork_bridge` dylib into itself with
# DynaLoader and calls its one entry point. The dylib does a single
# `MRMediaRemoteGetNowPlayingInfo` query and prints one JSON line
# (`{"art":"<base64>"}` or `{"art":null}`); everything else — track matching,
# caching, timeouts — lives on the Kotlin side that spawns this script.
#
# This is the source of truth; the Gradle `syncMacArtworkDriver` task copies
# it into the app resources. Usage:
#   /usr/bin/perl artwork-driver.pl /path/to/artwork-bridge.dylib
use strict;
use warnings;
use DynaLoader;

@ARGV == 1 or die "usage: $0 <artwork-bridge.dylib>\n";
my ($dylib) = @ARGV;
my $handle = DynaLoader::dl_load_file($dylib, 0)
    or die "cannot load artwork helper: $dylib\n";
my $symbol = DynaLoader::dl_find_symbol($handle, "qn_artwork_get")
    or die "entry point qn_artwork_get not found in $dylib\n";
DynaLoader::dl_install_xsub("main::qn_artwork_get", $symbol);
{
    no strict "refs";
    &{"main::qn_artwork_get"}();
}

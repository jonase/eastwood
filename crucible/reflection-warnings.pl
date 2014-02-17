: # use perl -*-Perl-*-
eval 'exec perl -S "$0" ${1+"$@"}'
    if 0;
# -*cperl-*-

use strict;
use Getopt::Long;
use File::Basename;
# File::Temp is a library for creating temporary files.
use File::Temp;
# FindBin is a library to find the directory in which the script is
# installed.  This can be useful for making it easy to install other
# auxiliary files needed by the script all in one place, and no matter
# where someone installs 'the whole package' of files, the script can
# find them, without needing hand-editing.
use FindBin;
# Below is an example use of the $FindBin::Bin string variable defined
# by the line above, to include the 'build' subdirectory beneath that
# in the list of directories searched when finding .pm files for later
# 'use' statements.  Useful if a .pm file is part of 'the whole
# package' you want to distribute with this script.
use lib "$FindBin::Bin" . "/build";


#my $debug = 1;

my $verbose = 0;
my $full_progname = $0;
my $progname = fileparse($full_progname);

sub usage {
    print STDERR
"usage: $progname [ --help ] [ --verbose ]
               [ file1 ... ]

description here
";
}

my $help = 0;
if (!GetOptions('help' => \$help,
		'verbose+' => \$verbose
		))
{
    usage();
    exit 1;
}
if ($help) {
    usage();
    exit 0;
}

my $num_same_msg = { };

while (<>) {
    if (/^\s*Reflection warning, (.*):(\d+):(\d+) - (.*)$/) {
	my ($fname, $line, $col, $msg) = ($1, $2, $3, $4);
#	my $k = $fname . ':' . 'reflection_warn' . ':' . $msg;
#	++$num_same_msg->{$k};
#	my $n = $num_same_msg->{$k};
	printf "Reflection warning, %s:line:col - %s %d:%d\n", $fname, $msg, $line, $col;
    } elsif (/^(.*):(\d+) recur arg for primitive local: (.*)$/) {
	my ($fname, $line, $msg) = ($1, $2, $3);
#	my $k = $fname . ':' . 'recur_arg' . ':' . $msg;
#	++$num_same_msg->{$k};
#	my $n = $num_same_msg->{$k};
	printf "%s:line recur arg for primitive local: %s %d\n", $fname, $msg, $line;
	# Print the following line, too.
	my $_ = <>;
	print;
    } else {
	# skip it
    }
}

exit 0;

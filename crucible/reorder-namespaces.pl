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

my $line = undef;
my $proj_name = undef;
my $ns_name = undef;
my $proj_lines = { };
my $ns_lines = { };
my $warn_lines = { };

BIG_LOOP:
while (defined($line) || ($line = <>)) {
    chomp $line;
    if ($line =~ /^=== (.*)$/) {
	$proj_name = $1;
	#printf STDERR "reading === at %d\n", $.;
	$proj_lines->{$proj_name} = ($line . "\n");
	# Get all following lines up to the next one that begins with ==
	while ($line = <>) {
	    chomp $line;
	    if (($line =~ /^=== /) ||
		($line =~ /^== Linting/) ||
		($line =~ /^== Warnings/))
	    {
		next BIG_LOOP;
	    }
	    $proj_lines->{$proj_name} .= ($line . "\n");
	}
	last BIG_LOOP;
    } elsif ($line =~ /^== Linting (.*)$/) {
	$ns_name = $1;
	#printf STDERR "reading linting at %d\n", $.;
	if (!defined($proj_name)) {
	    die (sprintf "Found namespace name '%s' at line %d before finding any project line:\n%s\n", $ns_name, $., $line);
	}
	$ns_lines->{$proj_name}{$ns_name} .= ($line . "\n");
	undef $line;
    } elsif ($line =~ /^== Warnings/) {
	# Get all following lines up to the next one that begins with ==
	#printf STDERR "reading warnings at %d\n", $.;
	$warn_lines->{$proj_name} = ($line . "\n");
	while ($line = <>) {
	    chomp $line;
	    if (($line =~ /^=== /) ||
		($line =~ /^== Linting/) ||
		($line =~ /^== Warnings/))
	    {
		next BIG_LOOP;
	    }
	    $warn_lines->{$proj_name} .= ($line . "\n");
	}
	#printf STDERR "Got here 2 at %d line='%s' defined(\$line)='%s'\n", $., $line, defined($line) ? "defined" : "undef";
	undef $line;
	last BIG_LOOP;
    } else {
	if (defined($proj_name) && defined($ns_name)) {
	    $ns_lines->{$proj_name}{$ns_name} .= ($line . "\n");
	} else {
	    printf "Ignoring line %d: %s\n", $., $line;
	}
	undef $line;
    }
}

foreach my $proj_name (sort keys %{$ns_lines}) {
    printf "%s", $proj_lines->{$proj_name};
    foreach my $ns_name (sort keys %{$ns_lines->{$proj_name}}) {
	printf "%s", $ns_lines->{$proj_name}{$ns_name};
    }
    printf "%s", $warn_lines->{$proj_name};
}

exit 0;

DEX-forensics
=============

DEX: Digital Evidence Provenance Supporting Reproducibility and   Comparison


Introduction
------------

DEX is a system for identifying and comparing digital evidence, and is
fully described in:

  DEX: Digital Evidence Provenance Supporting Reproducibility and
  Comparison.  Brian Neil Levine and Marc Liberatore. In Proc. of
  DFRWS Annual Conference, August 2009.

which can be found at:

  http://www.dfrws.org/

More information about DEX can be found at:

  http://forensics.umass.edu/projects.php

The DEX tools are implemented as a set of Java wrappers around
command-line forensic tools, converting their output into a
standardized XML format. Comparisons between DEX-wrapped tool outputs
are also supported supported.

The current version of DEX is a research prototype, and rough
around the edges. We hope to improve the user interface, broaden the
list of supported tools, and extend DEX to cover current and emerging
use cases. We welcome your comments and contributions.


Building
--------

DEX must currently be built manually.  Ant or maven support is planned
for a future release.  For now, navigate the to the src directory, and
compile the tools you wish to use directly.  For example:

cd src
javac fdisk/Fdisk.java

Note that DEX depends upon the JDOM, XMLUnit, and JArgs libraries,
which are open-source and available separately on the Internet.  Be
sure they are in your class path before building DEX.


Using
-----

DEX wrapper classes are named after the tool they wrap.  For example,
Fdisk wraps fdisk.

Most of the DEX classes that are invoked on the command line include
help if run with a -h option, and typically they are straightforward
wrappers around the underlying tool.

As noted in the DEX paper, DEX tools can be chained together to
include evidence from tools at multiple layers; typically, the
"--input-dex" option a wrapper to add to a DEX (XML) file that another
tool has output.


For More Information
--------------------

DEX was written by Brian Neil Levine (@cs.umass.edu) and Marc
Liberatore (@cs.umass.edu), with contributions from other.
Contact Brian and/or Marc with feedback, questions, patches, etc.

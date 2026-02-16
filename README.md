# Nemo Studio Desktop for NVIDIA DGX Spark

Many non technical users are keen to get hands on state of the art hardware such as NVIDIA DGX Spark and struggle to follow the tutorials or jupyter notebook examples, it's even more complex for end to end workflows like the ones supported by NVIDIA Nemo.

Nemo Studio Desktop (NSD) acts as your personal devops specialized in preparing all the prerequisites for working with NVIDIA Nemo, it runs on your local computer and connects to your DGX Spark, after installing you'll have a shorthand at hand that helps you get ready to work with NVIDIA Nemo.

![](assets/20260216_020626_gtcdc25-nemo-diagram.svg)


## Features (demo)

- **Remote Connection**: Connects to your DGX Spark and run remote commands to setup NVIDIA Nemo
- **End to End Workflow Validation**: NSD can prepare each step of NVIDIA Nemo workflow.
- **End to End Example**: Once all the steps are ready NSD can prepare a basic end to end example as a starting template.
- **Connects to existing NVIDIA Nemo tools**: NSD is a complement of existing NVIDIA Nemo tools such as Nemo Agent Toolking (NAT), once the environment is ready and installed it will guide the user to open this official tools.
- **Learning Reinforcement**: NSD will take the user to the official documentation to clarify the concepts behind NVIDIA Nemo.

## Prerequisites (any OS)

- **OpenJDK 25** for this project (see below to use another version). **Maven is not required** (Maven Wrapper is included).


| OS      | Install OpenJDK 25        |
| --------- | --------------------------- |
| macOS   | `brew install openjdk@25` |
| Windows | Still not tested          |
| Linux   | Still not tested          |

### Switching to another Java version later

The project uses **`.java-version`** (currently set to **25**). To use a different JDK:

1. **Edit `.java-version`** — change `25` to the version you want (e.g. `21` or `17`).
2. **Install that JDK** if needed (e.g. `brew install openjdk@21` on macOS).
3. **Run the app** with `./run.sh` (macOS/Linux) or `.\run.ps1` (Windows). The run scripts read `.java-version` and set **JAVA_HOME** for that run.

**macOS:** `set-java.sh` uses `/usr/libexec/java_home -v <version>`, so any JDK installed via Homebrew (`openjdk@17`, `openjdk@21`, `openjdk@25`) works after you change `.java-version`.

**Windows:** still not tested.

**Linux:** still not tested.

### Installing and switching multiple OpenJDK versions (OS level)

Ways to have several JDKs installed and switch between them on your machine:

**macOS — Homebrew (simple, no extra tools)**

```bash
# Install several versions (they live side by side)
brew install openjdk@17 openjdk@21 openjdk@25

# See what’s installed and their paths
/usr/libexec/java_home -V

# Use a specific version in the current shell (for this terminal only)
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

Then run `./run.sh` or any Maven/Java command; they’ll use that JDK until you close the terminal or change `JAVA_HOME`.

**macOS / Linux — jenv (switch per directory or globally)**

[jenv](https://www.jenv.be/) manages multiple Java versions and can set **JAVA_HOME** automatically per directory (using `.java-version`) or globally.

```bash
# Install jenv (macOS)
brew install jenv
echo 'export PATH="$HOME/.jenv/bin:$PATH"' >> ~/.zshrc
echo 'eval "$(jenv init -)"' >> ~/.zshrc

# Add JDKs (point to Homebrew installs or any JDK path)
jenv add /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
jenv add /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# List versions
jenv versions

# Use 25 globally, or only in current directory (creates/uses .java-version)
jenv global 25
# or: jenv local 25
```

After `jenv local 25`, opening a terminal in that directory sets **JAVA_HOME** to 25 automatically. This project’s **`.java-version`** is already set to `25`; jenv will pick it up if you use jenv in that directory.

**Any OS — SDKMAN**

[SDKMAN](https://sdkman.io/) installs and switches JDKs (and other tools) from the command line.

```bash
# Install SDKMAN (macOS/Linux)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# List available Java versions and install a few
sdk list java
sdk install java 25.0.1-tem
sdk install java 21.0.2-tem

# Use one for current shell
sdk use java 25.0.1-tem

# Or set default
sdk default java 25.0.1-tem
```

**Windows**

- Still not tested

**Linux (Debian/Ubuntu)**

- Still not tested

## Run the application

**No Maven install needed** — use the Maven Wrapper (first run will download Maven and dependencies):

- **macOS / Linux:**
  `./mvnw javafx:run`
  or `./run.sh`
- **Windows (cmd or PowerShell):**
  still not tested

If you have Maven installed, you can still run: `mvn javafx:run`.

First run may take a minute (downloads Maven + JavaFX); later runs are quick.

## Build (optional)

```bash
./mvnw compile
./mvnw package
```

On Windows: still not tested

## Project layout

```
nemostudiodesktop/
├── .java-version            # Java version for this project (edit to switch; currently 25)
├── set-java.sh, set-java.ps1, set-java.cmd   # Set JAVA_HOME from .java-version (used by run scripts)
├── config/java-home        # Optional: full JDK path (Windows/Linux if auto-detect fails)
├── pom.xml                 # Maven: Java 25, JavaFX 21
├── mvnw, mvnw.cmd          # Maven Wrapper (no Maven install needed)
├── .mvn/wrapper/
├── run.sh, run.bat, run.ps1
├── README.md
└── src/main/
    ├── java/com/nemostudio/ide/
    │   ├── NemoStudioApp.java
    │   └── IdeView.java
    └── resources/
        └── styles/
            └── ide.css
```

You can extend this with more IDE features (tabs, file open/save, syntax highlighting, etc.) later.

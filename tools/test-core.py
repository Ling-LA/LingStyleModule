from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
classes=root/'build/core-tests'
classes.mkdir(parents=True,exist_ok=True)
sources=list((root/'app/src/main/java/io/github/ling/style/core').glob('*.java'))+[root/'app/src/main/java/io/github/ling/style/hook/Host.java',root/'tests/CoreChecks.java']
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True)
subprocess.run(['java','-cp',str(classes),'CoreChecks'],check=True)

from pathlib import Path
import subprocess
import argparse,os
parser=argparse.ArgumentParser()
parser.add_argument('--json-jar',type=Path,help='JVM org.json jar for JSON gray-tip and offline reply-engine regression checks')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
classes=root/'build/core-tests'
classes.mkdir(parents=True,exist_ok=True)
sources=list((root/'app/src/main/java/io/github/ling/style/core').glob('*.java'))+[root/'app/src/main/java/io/github/ling/style/hook/Host.java',root/'app/src/main/java/io/github/ling/style/hook/PokeEvent.java',root/'tests/CoreChecks.java',root/'tests/ReplyChecks.java']
subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),*map(str,sources)],check=True)
subprocess.run(['java','-cp',str(classes),'CoreChecks'],check=True)
classpath=str(classes)+(os.pathsep+str(args.json_jar.resolve()) if args.json_jar else '')
subprocess.run(['java','-cp',classpath,'io.github.ling.style.hook.ReplyChecks',*(['json'] if args.json_jar else [])],check=True)
if args.json_jar:
    subprocess.run(['javac','-encoding','UTF-8','-cp',classpath,'-d',str(classes),str(root/'app/src/main/java/io/github/ling/style/engine/ReplyClient.java'),str(root/'tests/StyleOutputChecks.java')],check=True)
    subprocess.run(['java','-cp',classpath,'io.github.ling.style.engine.StyleOutputChecks'],check=True)
else:
    print('SKIP reply-engine regression checks: pass --json-jar with a JVM org.json implementation')

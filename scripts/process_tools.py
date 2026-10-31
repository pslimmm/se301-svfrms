"""Bounded subprocesses with timeout cleanup limited to the owned process tree."""
import os
from pathlib import Path
import signal
import subprocess


def run(command, cwd, log, timeout=180):
    process = subprocess.Popen(command, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               start_new_session=os.name != 'nt')
    try:
        stdout, stderr = process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
        else:
            os.killpg(process.pid, signal.SIGKILL)
        stdout, stderr = process.communicate()
        Path(log).write_bytes(stdout+b'\nSTDERR:\n'+stderr+b'\nTIMEOUT\n')
        raise RuntimeError('Owned subprocess timed out: '+str(command[0]))
    Path(log).write_bytes(stdout+b'\nSTDERR:\n'+stderr)
    return subprocess.CompletedProcess(command,process.returncode,stdout,stderr)


def maven(*args):
    return ['cmd.exe','/d','/c','mvn',*args] if os.name=='nt' else ['mvn',*args]

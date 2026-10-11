"""A prelude alert must be false even when its label matches the later event."""
import pathlib
import subprocess
import sys
import tempfile

preferred = pathlib.Path('/tmp/opencode')
with tempfile.TemporaryDirectory(prefix='replay-metrics-', dir=preferred if preferred.is_dir() else None) as directory:
    data = pathlib.Path(directory) / 'fixture.tsv'
    data.write_text('kind\tscenario\tfile\ttruth\tstart\tend\temission\ttrigger\tpredicted\tvote\tdistance\talert\n'
                    'RECORD\tbed\ttarget\tscream_distress\t3\t5\t0\t0\t-\t0\t0\tfalse\n'
                    'EVENT\tbed\ttarget\tscream_distress\t3\t5\t1\t1\tscream_distress\t1\t0\ttrue\n'
                    'EVENT\tbed\ttarget\tscream_distress\t3\t5\t4\t4\tscream_distress\t1\t0\ttrue\n'
                    'RECORD\tbed\tnoise\tbackground\t3\t10\t0\t0\t-\t0\t0\tfalse\n'
                    'EVENT\tbed\tnoise\tbackground\t3\t10\t4\t4\texplosion\t1\t0\ttrue\n')
    result = subprocess.check_output([sys.executable, str(pathlib.Path(__file__).with_name('summarize_replay.py')), str(data)], text=True)
    assert 'recall=1/1; alert classification precision=1/3' in result
    assert 'including target preludes)=2;' in result
    assert 'exposure=13.00s; false alerts/hour=553.85' in result
    assert 'n=1, median=1.0, max=1.0' in result
print('PASS: replay metric accounting, prelude false alerts and insertion latency')

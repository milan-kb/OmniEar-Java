# False-alert investigation, starting at 747f7e8

The retained RMS-gated 2.5 s FFT/MFCC k=7 pipeline is the baseline. No periodic,
onset-only or hybrid scheduling is reintroduced. Both inspected sets (old 50 and
previous final 41), plus eight development backgrounds, are development now.
Investigate rejection evidence on the exact baseline decisions: nearest winning
class vs any-class distance, source-group-out distance calibration, relative
background distance, and a separate real hard-negative reference bank. Negative
references do not change original feature scaling or inverse-frequency votes.
Also compare temporal confirmation on consecutive baseline capture decisions.

Select only policies preserving every baseline correctly detected target interval
on every development scenario while reducing background alerts. Record precision,
abstentions and latency as well as recall. Freeze before evaluating a newly
acquired uploader/source-independent final set. Final results decide accept/reject,
never tune thresholds. Replays/gain changes are not independent recordings and
file insertion is not an annotated acoustic onset. No emergency reliability claim.

Fresh metadata-only selection: 48 recordings, 8 screams / 8 breakages / 2
recorded traffic sirens / 30 backgrounds, one uploader per recording. All CC0
or CC BY 3.0. No confidently documented unused independently sourced explosion
was found in the reviewed permissible FSD50K metadata: remaining candidates were
fireworks, gunshots, synthesized/composite effects or ambiguous. Do not relabel
them. Explosion-specific behavior must remain unchanged; final explosion recall
is unmeasured, not 0% or 100%. New set is `datasets/rejection-final/`; do not read
its audio for model decisions until the candidate is frozen.

## Frozen candidate and acceptance

Keep all 170 training recordings, features, scaling, k=7 votes, capture scheduling
and vote gates. Reject alert eligibility (retain predicted category and vote) if
nearest background distance is strictly less than 0.9 times nearest winning-class
distance for screams, or 1.0 times for impacts. Equal-distance ties remain eligible.
Other categories have no new gate. Persist the explicit rejection reason.

Development: all 199 baseline-correct recording/scenario intervals survive
(35 direct and 164 streaming); background alerts across these repeated scenarios
fall 99 to 94, including direct 9 to 7. No correctly routed alert is delayed.
The final candidate must preserve every baseline-correct interval in each scenario,
never increase a scenario's background-alert count, and reduce the total number
of streaming background alerts. Otherwise restore the baseline. Precision and
latency are reported, including any extra missed/abstained recordings. These are
finite-set acceptance rules, not statistical evidence of general reliability.

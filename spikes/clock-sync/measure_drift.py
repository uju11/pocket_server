"""
S4 drift measurement — pure synchronous simulation (no asyncio, no network).
Models NTP-lite clock offset estimation and event delivery latency for two clients.
Writes drift_results.txt with the outcome.
"""
import random, statistics, time

random.seed(42)

def now_ms():
    return time.monotonic() * 1000.0

# Server has a wall-clock skew vs each client's monotonic clock
server_skew_ms = random.uniform(-80, 80)  # server clock differs from client monotonic

def server_clock():
    return now_ms() + server_skew_ms

def rtt_ms():
    """Simulated LAN round-trip: 1-5ms"""
    return random.uniform(1, 5)

def ntp_lite_offset(label, iters=5):
    """Estimate server offset from client's perspective using NTP-lite."""
    offsets = []
    for _ in range(iters):
        t0 = now_ms()
        one_way = rtt_ms() / 2
        ts = server_clock() + one_way  # server sends after half-RTT
        t2 = t0 + rtt_ms()            # client receives after full RTT
        rtt = t2 - t0
        offset = ts - (t0 + rtt / 2)
        offsets.append(offset)
    offsets.sort()
    median = offsets[len(offsets) // 2]
    print(f"[{label}] NTP-lite offsets={[f'{o:.2f}' for o in offsets]} median={median:.2f}ms")
    return median

# --- clients estimate server offset independently
offset_a = ntp_lite_offset("A")
offset_b = ntp_lite_offset("B")

results = []

for event_type, position in [("play", 0.0), ("seek", 42.5)]:
    srv_time = server_clock()

    # Client A receives event after one-way latency
    la = rtt_ms() / 2
    adj_now_a = now_ms() + offset_a
    lat_a = (adj_now_a + la) - srv_time

    # Client B receives event after (independent) one-way latency
    lb = rtt_ms() / 2
    adj_now_b = now_ms() + offset_b
    lat_b = (adj_now_b + lb) - srv_time

    drift = abs((adj_now_a + la) - (adj_now_b + lb))
    results.append({"event": event_type, "lat_a": lat_a, "lat_b": lat_b, "drift": drift})
    print(f"[A] {event_type:5s}  latency={lat_a:.2f}ms")
    print(f"[B] {event_type:5s}  latency={lat_b:.2f}ms")

print(f"\n{'─'*55}")
print(f"{'Event':<8} {'lat_A':>10} {'lat_B':>10} {'drift':>10}")
print(f"{'─'*55}")
for r in results:
    print(f"{r['event']:<8} {r['lat_a']:>10.2f} {r['lat_b']:>10.2f} {r['drift']:>10.2f}")

drifts = [r["drift"] for r in results]
mx = max(drifts)
mean = statistics.mean(drifts)
passed = mx < 300
print(f"\nMax drift : {mx:.2f} ms")
print(f"Mean drift: {mean:.2f} ms")
print(f"PASS (<300ms): {passed}")
print(f"{'─'*55}")

with open("drift_results.txt", "w") as f:
    f.write(f"S4 clock-sync spike — simulated LAN (1-5ms one-way, server_skew={server_skew_ms:.1f}ms)\n")
    f.write(f"max_drift={mx:.2f}ms  mean_drift={mean:.2f}ms  pass={passed}\n")
    for r in results:
        f.write(f"  {r['event']}: A_lat={r['lat_a']:.2f}ms B_lat={r['lat_b']:.2f}ms drift={r['drift']:.2f}ms\n")
print("Results written to drift_results.txt")

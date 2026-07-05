/**
 * Per-connection token-bucket rate limiter.
 *
 * Guards the socket event handlers against flooding / packet-injection spam from
 * a hostile or broken client. Each socket gets its own bucket; every inbound
 * event consumes one token. Buckets refill continuously. When a socket runs dry
 * it receives a RATE_LIMITED error and, if it keeps flooding well past the limit,
 * is disconnected outright.
 */
export class TokenBucket {
  private tokens: number;
  private lastRefill: number;
  /** Count of consecutive rejected events (resets when a token is granted). */
  private strikes = 0;

  constructor(
    private readonly capacity: number,
    private readonly refillPerSecond: number,
  ) {
    this.tokens = capacity;
    this.lastRefill = Date.now();
  }

  /** Try to consume one token. Returns true when the event may proceed. */
  take(): boolean {
    const now = Date.now();
    const elapsed = (now - this.lastRefill) / 1000;
    this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillPerSecond);
    this.lastRefill = now;

    if (this.tokens >= 1) {
      this.tokens -= 1;
      this.strikes = 0;
      return true;
    }
    this.strikes += 1;
    return false;
  }

  /** True when the client kept sending long after being told to stop. */
  get abusive(): boolean {
    return this.strikes > 30;
  }
}

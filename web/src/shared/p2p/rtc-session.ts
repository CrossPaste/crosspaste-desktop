/**
 * WebRTC DataChannel session for P2P communication.
 *
 * Handles offer/answer creation, ICE gathering, and DataChannel management.
 * Designed for LAN use — no STUN/TURN servers.
 */

import {
  extractCompact,
  encodeCompact,
  decodeCompact,
  buildSDP,
} from "./rtc-codec";

export interface RTCSessionCallbacks {
  onStateChange: (state: SessionState) => void;
  onMessage: (msg: string) => void;
  onLocalCode: (code: string) => void;
  onError: (err: string) => void;
}

export type SessionState =
  | "idle"
  | "gathering"
  | "waiting-for-peer"
  | "connecting"
  | "connected"
  | "disconnected"
  | "failed";

const ICE_GATHER_TIMEOUT_MS = 10_000;

export class RTCSession {
  private pc: RTCPeerConnection | null = null;
  private dc: RTCDataChannel | null = null;
  private cb: RTCSessionCallbacks;

  constructor(cb: RTCSessionCallbacks) {
    this.cb = cb;
  }

  /** Side A: create an offer and wait for ICE gathering */
  async createOffer(): Promise<void> {
    this.cb.onStateChange("gathering");

    this.pc = new RTCPeerConnection({ iceServers: [] });
    this.setupDCFromLocal();

    const offer = await this.pc.createOffer();
    await this.pc.setLocalDescription(offer);

    await this.waitForICE();

    const sdp = this.pc.localDescription!.sdp;
    console.log("[P2P] Local offer SDP:\n", sdp);
    const compact = extractCompact(sdp);
    console.log("[P2P] Extracted compact:", JSON.stringify(compact));
    const code = encodeCompact(compact);

    this.cb.onLocalCode(code);
    this.cb.onStateChange("waiting-for-peer");
  }

  /** Side A: accept the answer code from Side B */
  async acceptAnswer(code: string): Promise<void> {
    if (!this.pc) throw new Error("No active connection");

    this.cb.onStateChange("connecting");

    const remote = decodeCompact(code);
    // Answerer uses setup:active, offerer uses setup:actpass
    const sdp = buildSDP(remote, "answer");
    console.log("[P2P] Remote answer SDP:\n", sdp);
    await this.pc.setRemoteDescription({ type: "answer", sdp });

    // Add the remote candidate explicitly
    await this.pc.addIceCandidate(
      new RTCIceCandidate({
        candidate: `candidate:1 1 udp 2130706431 ${remote.i} ${remote.o} typ host`,
        sdpMid: "0",
        sdpMLineIndex: 0,
      }),
    );

    this.watchConnection();
  }

  /** Side B: accept the offer code from Side A and produce an answer */
  async acceptOffer(code: string): Promise<void> {
    this.cb.onStateChange("gathering");

    const remote = decodeCompact(code);

    this.pc = new RTCPeerConnection({ iceServers: [] });
    this.setupDCFromRemote();

    const offerSdp = buildSDP(remote, "offer");
    console.log("[P2P] Remote offer SDP:\n", offerSdp);
    await this.pc.setRemoteDescription({ type: "offer", sdp: offerSdp });

    // Add the remote candidate explicitly
    await this.pc.addIceCandidate(
      new RTCIceCandidate({
        candidate: `candidate:1 1 udp 2130706431 ${remote.i} ${remote.o} typ host`,
        sdpMid: "0",
        sdpMLineIndex: 0,
      }),
    );

    const answer = await this.pc.createAnswer();
    await this.pc.setLocalDescription(answer);

    await this.waitForICE();

    const sdp = this.pc.localDescription!.sdp;
    const compact = extractCompact(sdp);
    const answerCode = encodeCompact(compact);

    this.cb.onLocalCode(answerCode);
    this.cb.onStateChange("connecting");
    this.watchConnection();
  }

  /** Send a message over the DataChannel */
  send(msg: string): boolean {
    if (!this.dc || this.dc.readyState !== "open") return false;
    this.dc.send(msg);
    return true;
  }

  /** Close everything */
  close(): void {
    this.dc?.close();
    this.pc?.close();
    this.dc = null;
    this.pc = null;
    this.cb.onStateChange("disconnected");
  }

  // ─── Private ──────────────────────────────────────────────────────────

  /** Offerer creates the DataChannel */
  private setupDCFromLocal(): void {
    this.dc = this.pc!.createDataChannel("crosspaste", {
      ordered: true,
    });
    this.bindDC(this.dc);
  }

  /** Answerer receives the DataChannel */
  private setupDCFromRemote(): void {
    this.pc!.ondatachannel = (event) => {
      this.dc = event.channel;
      this.bindDC(this.dc);
    };
  }

  private bindDC(dc: RTCDataChannel): void {
    dc.onopen = () => {
      this.cb.onStateChange("connected");
    };
    dc.onmessage = (event) => {
      this.cb.onMessage(String(event.data));
    };
    dc.onclose = () => {
      this.cb.onStateChange("disconnected");
    };
    dc.onerror = () => {
      this.cb.onStateChange("failed");
      this.cb.onError("DataChannel error");
    };
  }

  /** Wait for ICE gathering to complete or timeout */
  private waitForICE(): Promise<void> {
    const pc = this.pc!;
    if (pc.iceGatheringState === "complete") return Promise.resolve();

    return new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        // Check if we have at least one host candidate
        const sdp = pc.localDescription?.sdp ?? "";
        if (sdp.includes("typ host")) {
          resolve();
        } else {
          reject(new Error("ICE gathering timeout — no host candidates found"));
        }
      }, ICE_GATHER_TIMEOUT_MS);

      pc.onicegatheringstatechange = () => {
        if (pc.iceGatheringState === "complete") {
          clearTimeout(timeout);
          resolve();
        }
      };
    });
  }

  /** Watch ICE connection state for connect/disconnect */
  private watchConnection(): void {
    const pc = this.pc!;
    pc.oniceconnectionstatechange = () => {
      switch (pc.iceConnectionState) {
        case "connected":
        case "completed":
          // DataChannel onopen will trigger "connected" state
          break;
        case "disconnected":
          this.cb.onStateChange("disconnected");
          break;
        case "failed":
          this.cb.onStateChange("failed");
          this.cb.onError("ICE connection failed — check network / firewall");
          break;
      }
    };
  }
}

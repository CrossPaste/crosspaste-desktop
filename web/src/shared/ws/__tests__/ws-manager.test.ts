import { describe, it, expect, vi, beforeEach } from "vitest";
import { WsManager } from "../ws-manager";
import { DeviceStore, type StoredDevice } from "@/shared/storage/device-store";

vi.mock("@/shared/storage/device-store", () => ({
  DeviceStore: {
    getAll: vi.fn(async () => []),
  },
}));

function makeDevice(overrides: Partial<StoredDevice>): StoredDevice {
  return {
    targetAppInstanceId: "device-1",
    syncInfo: {} as StoredDevice["syncInfo"],
    host: "127.0.0.1",
    port: 13129,
    trusted: true,
    addedAt: 0,
    ...overrides,
  };
}

describe("WsManager.connectAllDevices", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("skips devices flagged needsRePair so decrypt-fail recovery does not reconnect-loop", async () => {
    vi.mocked(DeviceStore.getAll).mockResolvedValue([
      makeDevice({ targetAppInstanceId: "healthy" }),
      makeDevice({ targetAppInstanceId: "awaiting-re-pair", needsRePair: true }),
      makeDevice({ targetAppInstanceId: "untrusted", trusted: false }),
    ]);

    const manager = new WsManager("self-instance");
    const connectSpy = vi
      .spyOn(manager, "connectDevice")
      .mockResolvedValue(true);

    await manager.connectAllDevices();

    expect(connectSpy).toHaveBeenCalledTimes(1);
    expect(connectSpy.mock.calls[0][0].targetAppInstanceId).toBe("healthy");
  });
});

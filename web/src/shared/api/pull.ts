import { apiGetText, type RequestConfig } from "./client";
import { type PasteData, parsePasteData } from "@/shared/models/paste-data";

export const PullApi = {
  /** Pull latest paste from the connected device */
  async pullPaste(config: {
    host: string;
    port: number;
    appInstanceId: string;
    targetAppInstanceId: string;
  }): Promise<PasteData | null> {
    const reqConfig: RequestConfig = {
      host: config.host,
      port: config.port,
      appInstanceId: config.appInstanceId,
      targetAppInstanceId: config.targetAppInstanceId,
    };
    try {
      const text = await apiGetText(reqConfig, "/pull/paste");
      if (!text) return null;
      return parsePasteData(text);
    } catch {
      return null;
    }
  },

  /** Get the source application icon */
  async pullIcon(
    config: {
      host: string;
      port: number;
      appInstanceId: string;
      targetAppInstanceId: string;
    },
    source: string,
  ): Promise<string | null> {
    const url = `http://${config.host}:${config.port}/pull/icon/${encodeURIComponent(source)}`;
    try {
      const res = await fetch(url, {
        headers: {
          appInstanceId: config.appInstanceId,
          targetAppInstanceId: config.targetAppInstanceId,
        },
      });
      if (!res.ok) return null;
      const blob = await res.blob();
      return URL.createObjectURL(blob);
    } catch {
      return null;
    }
  },
};

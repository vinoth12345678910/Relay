import { z } from 'zod';

/** '' (same origin, via the dev proxy/nginx) or an absolute http(s) URL. */
export const baseUrlSchema = z
  .string()
  .trim()
  .refine((v) => {
    if (v === '') return true;
    try {
      const u = new URL(v);
      return (
        (u.protocol === 'http:' || u.protocol === 'https:') &&
        !u.username &&
        !u.password &&
        !u.search &&
        !u.hash
      );
    } catch {
      return false;
    }
  }, 'Enter an http(s) URL like http://localhost:8080, or leave empty to use this origin')
  .transform((v) => v.replace(/\/+$/, ''));

export const connectSchema = z.object({
  baseUrl: baseUrlSchema,
  apiKey: z.string().trim().min(1, 'Enter the API key for your app'),
  remember: z.boolean(),
});
export type ConnectInput = z.input<typeof connectSchema>;
export type ConnectValues = z.output<typeof connectSchema>;

export const createAppSchema = z.object({
  name: z.string().trim().min(1, 'Give the app a name'),
  adminToken: z.string().min(1, 'The admin token is required to create an app'),
});
export type CreateAppValues = z.infer<typeof createAppSchema>;

/** True when requests will leave this origin and therefore depend on the backend's CORS config. */
export function isCrossOrigin(baseUrl: string, origin: string): boolean {
  if (baseUrl.trim() === '') return false;
  try {
    return new URL(baseUrl).origin !== origin;
  } catch {
    return false;
  }
}

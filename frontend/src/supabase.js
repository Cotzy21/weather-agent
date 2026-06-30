import { createClient } from '@supabase/supabase-js'

// URL + anon key kommer fra Vite-env (frontend/.env, ikke i git). anon key er
// offentlig og trygg i frontend; det er backend/RLS som styrer faktisk tilgang.
const url = import.meta.env.VITE_SUPABASE_URL
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

// Null hvis ikke konfigurert, så appen ikke krasjer før Supabase er satt opp.
export const supabase = url && anonKey ? createClient(url, anonKey) : null

// Authorization-header med gjeldende access-token (eller tomt objekt).
export async function authHeaders() {
  if (!supabase) return {}
  const { data } = await supabase.auth.getSession()
  const token = data?.session?.access_token
  return token ? { Authorization: `Bearer ${token}` } : {}
}

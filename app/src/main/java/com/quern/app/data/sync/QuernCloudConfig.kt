package com.quern.app.data.sync

/**
 * Shadow Studio's hosted Quern account server (Supabase).
 *
 * Both values are public by design: the publishable key only lets a client talk to the API,
 * and every table is locked with Row Level Security so a signed-in user can reach only their
 * own rows (see sync/supabase_schema.sql). Never put a secret / service_role key here.
 */
object QuernCloudConfig {
    const val SUPABASE_URL = "https://xgmrkjtwghmiipfaista.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_kjluxzYoZc51QHgtJPwtLA_Ybor3Uax"

    /** Where email confirmation and password-reset links land (the Quern website). */
    const val WEBSITE_URL = "https://quern.pages.dev"
}

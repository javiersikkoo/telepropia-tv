// Sin consola de fondo en Windows al abrir la app.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    tauri::Builder::default()
        // El resolver no manda CORS: todas las peticiones salen por aquí y no por el webview.
        .plugin(tauri_plugin_http::init())
        .run(tauri::generate_context!())
        .expect("no se ha podido arrancar TelePropia");
}

# Shaft Schematic App

An Android app written in **Kotlin** using **Jetpack Compose** that generates
technical schematics for boat shafts and exports them as PDFs.

## ✨ Features

- **Dynamic input form** for shaft specifications:
    - Overall length, shaft diameter, shoulder length, chamfer
    - Body segments with variable diameters
    - Keyways with position, width, depth, and length
    - Tapers (forward & aft) with large/small end diameters, length, and taper ratio (e.g., 1:10)
    - Threads (forward & aft) with diameter, pitch, and length
    - Liners (up to 3) with position, length, and outer diameter
- **Unit selection** (millimeters or inches) with automatic conversion
- **Dynamic add/remove** functionality for body segments, keyways, and liners
- **PDF composer** that draws shaft schematics with dimension arrows and a simple title block
- **Export to Documents folder** as PDF
- **PDF viewer** inside the app:
    - Lists saved PDFs
    - Open in any installed PDF viewer
    - Share via email, chat, etc.

## 🛠 Tech Stack

- **Kotlin** + **Jetpack Compose** (Material3)
- **ViewModel + StateFlow** for reactive state management
- **PDF export** with Android Canvas
- **FileProvider** for safe PDF sharing
- **Scoped storage** (saves under `Documents/` in app files)

## 📂 Project Structure
app/
├── src/main/java/com/android/shaftschematic/
│ ├── data/ # Data classes (ShaftSpecMm, BodySegmentSpec, etc.)
│ ├── pdf/ # ShaftPdfComposer & PDF helpers
│ ├── ui/screens/ # Compose UI screens (ShaftScreen, PdfListScreen)
│ └── ui/viewmodel/ # ShaftViewModel (state & business logic)
├── src/main/res/ # Material theme, icons, layouts, etc.
└── AndroidManifest.xml

📄 Roadmap / TODOs
- Navigation between Shaft input screen and PDF list screen
- More detailed dimensioning in PDF output
- Improved title block with metadata (date, project, author)
- Public Documents/Downloads export via MediaStore
- Unit tests for ViewModel & math functions
- Theming refinements
 
📜 License

This project is for educational and personal use.
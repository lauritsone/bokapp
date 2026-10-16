# Shelfmark — a reading log for Android

Scan a book's barcode (or photograph its cover), rate it, and write down what you thought.
Everything is stored on the phone.

## Features

- **Barcode scanning**: live, on-device ISBN reading with ML Kit. Hold the back cover up and it's captured in about a second; a short vibration confirms it.
- **Automatic lookup**: title, author, cover, publisher, page count and description come from Google Books, with Open Library as fallback. Neither needs an API key.
- **Cover photo mode**: for books without a barcode, snap the cover and type the details. You can add an ISBN later and tap search to fill the rest in.
- **Type the ISBN**: for worn or missing barcodes. ISBN-10 is converted to ISBN-13, and check digits are validated.
- **1–5 star rating**: tap to rate, tap the same star again to clear. Shown in the list and on each book.
- **Your notes**: a serif writing area for thoughts, quotes and recommendations. Notes are searchable.
- **Library**: search by title, author, ISBN or notes. Sort by recently added, highest rated, title or author.
- **Shelves**: keep books under *Read*, *Reading* or *Want to read*. Switch shelves in the library; a book scanned from a shelf lands on it, and you can move it from the book's page.
- **Export**: save the whole library as a spreadsheet (CSV, opens in Excel or Google Sheets, with cover links) or a JSON backup, to Downloads, Drive or anywhere else.
- **Import**: add books from a Shelfmark JSON backup or a CSV file: Shelfmark's own export, a spreadsheet (comma or semicolon separated) with a title column, or a Goodreads library export. Books already in the library are skipped.
- **New releases for you**: new Norwegian books from the National Library's catalogue (nb.no) by your authors and from your publishers, plus new English books from Open Library by your authors and the most-read new books in your genres. Each has a match percentage and the reasons behind it (authors you rated highly, genres you read, publishers). Reissues of older books are left out; filter by language, and add any of them to *Want to read* in one tap.
- **Covers that fill themselves in**: books imported or added without a cover get one looked up in the background (NB.no, Open Library, Google Books), by ISBN or by title and author.
- **English and Norwegian**: pick the language from the ⋮ menu, or follow the phone's language.
- **Duplicate check**: scanning a book you've already logged takes you to its page.
- Light and dark themes, edge-to-edge layout and TalkBack labels on all controls.

## Open and run

1. Open this folder in **Android Studio Ladybug (2024.2) or newer**.
2. Let Gradle sync (it downloads Gradle 8.11.1, AGP 8.7.3 and the libraries).
3. Run on a device with a camera (Android 8.0 / API 26 or newer). An emulator works for everything except real barcode scanning.

## Design

| Role | Choice | Why |
|---|---|---|
| Titles, headings, notes | **Literata** (variable, with optical sizes) | Google's typeface built for reading books on screens |
| Interface text | **Atkinson Hyperlegible Next** | Designed by the Braille Institute so letters like I/l/1 and O/0 never look alike |
| Palette | Bookplate indigo `#26336B`, cool paper `#F3F4F7`, marigold stars `#DB9112` | "Ex libris" stamp feel. The only warm colour is your rating. |

Body text is 17 sp with 25 sp line height; notes use 18 sp serif with 29 sp leading. Books without a cover image get a typeset cloth "binding" so the shelf never shows broken images.

Both fonts are under the SIL Open Font License (see `licenses/`).

## Project structure

```
app/src/main/java/app/shelfmark/
├── MainActivity.kt          Navigation (library → scan → new book → book page)
├── ShelfmarkApp.kt          Creates the database and repository
├── data/
│   ├── Book.kt              Room entity
│   ├── BookDao.kt           Queries
│   ├── BookLookup.kt        Google Books + Open Library ISBN lookup
│   ├── BookRepository.kt
│   └── Isbn.kt              ISBN-10/13 validation & conversion
└── ui/
    ├── theme/               Colours, typography, shapes
    ├── components/          BookCover, RatingBar
    ├── library/             Home list, search, sort
    ├── scan/                CameraX + ML Kit scanner, cover photo, ISBN dialog
    ├── edit/                Add / edit a book
    └── detail/              Book page with rating, notes, details
```

## Tech stack

Kotlin 2.0, Jetpack Compose with Material 3, Room, CameraX 1.4, ML Kit Barcode Scanning (bundled model, so it works offline), Coil and Navigation Compose. There's no DI framework: `ShelfmarkApp` holds the single repository.

## Ideas for next steps

- A finished date and a yearly reading goal
- Google Books API key in `BookLookup` if you hit the anonymous quota
- Let the user exclude books from the taste profile behind new-release suggestions, so one-off reads in an unusual genre don't skew the match scores (e.g. an "Ignore for suggestions" toggle on the book page)

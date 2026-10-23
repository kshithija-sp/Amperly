Reference photos for the debug vehicle gallery
==============================================

The gallery looks for a licensed side-profile photo at:

    app/src/main/assets/reference_photos/<make>-<model>.png
    app/src/main/assets/reference_photos/<make>-<model>.jpg
    app/src/main/assets/reference_photos/<make>-<model>.jpeg
    app/src/main/assets/reference_photos/<make>-<model>.webp

The slug is lower-case, with every run of non-alphanumeric characters replaced by a dash:

    Tata Nexon EV        -> tata-nexon-ev.png
    MG ZS EV             -> mg-zs-ev.jpg
    Isuzu D-Max V-Cross  -> isuzu-d-max-v-cross.png

Rules:
- Use a photo you have the right to ship: your own, a commissioned shot, or a licensed
  press/stock image whose licence permits redistribution in an app.
- Do not scrape manufacturer configurators, dealer sites or stock sites without a licence.
- Prefer a plain side profile on a neutral background so proportions are easy to compare.
- Keep the file small (roughly 800 px wide) because it ships inside the APK.
- No photo means the card shows a placeholder with the expected file name; that is intentional.

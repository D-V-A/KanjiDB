CREATE TABLE kanji_stroke (
    kanji_id INTEGER NOT NULL REFERENCES kanji(id) ON DELETE CASCADE,
    stroke_index INTEGER NOT NULL CHECK (stroke_index >= 1),
    path_data TEXT NOT NULL CHECK (length(trim(path_data)) > 0),
    PRIMARY KEY (kanji_id, stroke_index)
) WITHOUT ROWID;

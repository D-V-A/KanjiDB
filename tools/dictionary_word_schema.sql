-- Each lexical entry is the original JMdict source entry. No cross-entry merges.
CREATE TABLE word_entry (entry_id INTEGER PRIMARY KEY);
CREATE TABLE word_written (id INTEGER PRIMARY KEY, text TEXT NOT NULL UNIQUE);
CREATE TABLE word_entry_written (
    entry_id INTEGER NOT NULL REFERENCES word_entry(entry_id),
    written_id INTEGER NOT NULL REFERENCES word_written(id),
    written_order INTEGER NOT NULL,
    common INTEGER NOT NULL CHECK(common IN (0,1)),
    PRIMARY KEY(entry_id, written_id)
);
CREATE TABLE word_reading (
    id INTEGER PRIMARY KEY,
    entry_id INTEGER NOT NULL REFERENCES word_entry(entry_id),
    text TEXT NOT NULL,
    reading_order INTEGER NOT NULL,
    common INTEGER NOT NULL CHECK(common IN (0,1)),
    reading_priority INTEGER NOT NULL,
    UNIQUE(entry_id, reading_order), UNIQUE(entry_id, id)
);
CREATE TABLE word_entry_written_reading (
    id INTEGER PRIMARY KEY,
    entry_id INTEGER NOT NULL,
    written_id INTEGER NOT NULL,
    reading_id INTEGER NOT NULL,
    FOREIGN KEY(entry_id,written_id) REFERENCES word_entry_written(entry_id,written_id),
    FOREIGN KEY(entry_id,reading_id) REFERENCES word_reading(entry_id,id),
    UNIQUE(entry_id,written_id,reading_id), UNIQUE(entry_id,id)
);
CREATE TABLE word_sense (
    id INTEGER PRIMARY KEY,
    entry_id INTEGER NOT NULL REFERENCES word_entry(entry_id),
    sense_index INTEGER NOT NULL,
    UNIQUE(entry_id,sense_index), UNIQUE(entry_id,id)
);
CREATE TABLE word_meaning (
    id INTEGER PRIMARY KEY,
    sense_id INTEGER NOT NULL REFERENCES word_sense(id),
    language TEXT NOT NULL,
    meaning TEXT NOT NULL,
    gloss_order INTEGER NOT NULL,
    UNIQUE(sense_id,language,meaning)
);
-- Materialized stagk/stagr applicability to valid re_restr combinations.
CREATE TABLE word_sense_form (
    entry_id INTEGER NOT NULL,
    sense_id INTEGER NOT NULL,
    form_id INTEGER NOT NULL,
    FOREIGN KEY(entry_id,sense_id) REFERENCES word_sense(entry_id,id),
    FOREIGN KEY(entry_id,form_id) REFERENCES word_entry_written_reading(entry_id,id),
    PRIMARY KEY(sense_id,form_id)
);
CREATE TABLE word_written_kanji (
    written_id INTEGER NOT NULL REFERENCES word_written(id),
    kanji_id INTEGER NOT NULL REFERENCES kanji(id),
    position INTEGER NOT NULL,
    PRIMARY KEY(written_id,position)
);
CREATE INDEX idx_entry_written_written ON word_entry_written(written_id,entry_id);
CREATE INDEX idx_reading_text ON word_reading(text);
CREATE INDEX idx_form_written ON word_entry_written_reading(written_id,entry_id);
CREATE INDEX idx_form_reading ON word_entry_written_reading(reading_id);
CREATE INDEX idx_sense_form_form ON word_sense_form(form_id,sense_id);
CREATE INDEX idx_meaning_sense ON word_meaning(sense_id,gloss_order);
CREATE INDEX idx_meaning_language ON word_meaning(language);
CREATE INDEX idx_written_kanji_kanji ON word_written_kanji(kanji_id,written_id);

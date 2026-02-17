from nemo_curator.core.client import RayClient
from nemo_curator.pipeline import Pipeline
from nemo_curator.stages.text.io.reader import JsonlReader
from nemo_curator.stages.text.io.writer import JsonlWriter
from nemo_curator.stages.text.modules import ScoreFilter
from nemo_curator.stages.text.filters import (
    WordCountFilter,
    NonAlphaNumericFilter,
    RepeatedLinesFilter,
    PunctuationFilter,
    BoilerPlateStringFilter
)

# Start Ray client
ray_client = RayClient()
ray_client.start()

# Create processing pipeline
pipeline = Pipeline(name="quality_filtering")

# Load dataset - the starting point for all workflows
reader = JsonlReader(file_paths="input_data/")
pipeline.add_stage(reader)

# Standard quality filtering pipeline (most common)
# Remove too short/long documents (essential)
# and save the word_count field
word_count_filter = ScoreFilter(
    filter_obj=WordCountFilter(min_words=50, max_words=100000),
    text_field="text",
    score_field="word_count"
)
pipeline.add_stage(word_count_filter)

# Remove symbol-heavy content
alpha_numeric_filter = ScoreFilter(
    filter_obj=NonAlphaNumericFilter(max_non_alpha_numeric_to_text_ratio=0.25),
    text_field="text"
)
pipeline.add_stage(alpha_numeric_filter)

# Remove repetitive content
repeated_lines_filter = ScoreFilter(
    filter_obj=RepeatedLinesFilter(max_repeated_line_fraction=0.7),
    text_field="text"
)
pipeline.add_stage(repeated_lines_filter)

# Ensure proper sentence structure
punctuation_filter = ScoreFilter(
    filter_obj=PunctuationFilter(max_num_sentences_without_endmark_ratio=0.85),
    text_field="text"
)
pipeline.add_stage(punctuation_filter)

# Remove template/boilerplate text
boilerplate_filter = ScoreFilter(
    filter_obj=BoilerPlateStringFilter(),
    text_field="text"
)
pipeline.add_stage(boilerplate_filter)

# Add writer stage
writer = JsonlWriter(path="filtered_data/")
pipeline.add_stage(writer)

# Execute pipeline
results = pipeline.run()

# Cleanup Ray when done
ray_client.stop()
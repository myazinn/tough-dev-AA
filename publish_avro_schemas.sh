#!/bin/bash

SCHEMA_REGISTRY_URL="http://localhost:18081"
BASE_DIRECTORY="infra/schemas/"

# Iterate through each subdirectory in 'infra/schemas/'
for subject_dir in $(find "$BASE_DIRECTORY" -type d | sort); do
    subject=$(basename "$subject_dir")

    # Skip the base directory itself
    if [ "$subject" == "schemas" ]; then
        continue
    fi

    # Iterate through each AVRO schema file in the current subdirectory, sorted alphabetically
    for schema_file in $(find "$subject_dir" -name "*.avsc" | sort); do
        echo "Publishing schema: $schema_file for subject: $subject"

        # Use jq to wrap the schema in the desired format
        payload=$(jq -nc --arg schema "$(jq -c . < "$schema_file")" '{"schema": $schema}')

        curl -s \
            -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
            --data "$payload" \
            $SCHEMA_REGISTRY_URL/subjects/$subject/versions
    done
done

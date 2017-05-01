# source this to populate the local environment as necessary.
# needed to execute lein run or java -jar lcmap-changes.jar
export CLOWNFISH_DB_CONTACT_POINTS="localhost"
export CLOWNFISH_DB_KEYSPACE="lcmap_changes_local"
export CLOWNFISH_DB_PASSWORD="guest"
export CLOWNFISH_DB_USERNAME="guest"
export CLOWNFISH_EXCHANGE="local.lcmap.changes.server"
export CLOWNFISH_QUEUE="local.lcmap.changes.server"
export CLOWNFISH_HTTP_PORT=5778
export CLOWNFISH_RABBIT_HOST="localhost"
export CLOWNFISH_RABBIT_PORT=5672
export CLOWNFISH_CHIP_SPECS_URL="chip-specs.json"

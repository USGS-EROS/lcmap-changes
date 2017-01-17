CREATE KEYSPACE IF NOT EXISTS lcmap_changes_test
WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS lcmap_changes_test.algorithms(
    algorithm text PRIMARY KEY,
    inputs_host text,
    inputs_port int,
    enabled boolean
);

CREATE TABLE IF NOT EXISTS lcmap_changes_test.results(
    tile_x int,
    tile_y int,
    algorithm text,
    tile_status text STATIC,
    tile_status_last_updated timestamp STATIC,
    inputs_url text STATIC,
    inputs_md5 text STATIC,
    x int,
    y int,
    result text,
    result_md5 text,
    result_produced timestamp,
    result_status text,
    result_status_last_updated timestamp,
    PRIMARY KEY ((tile_x, tile_y, algorithm), x, y)
)
WITH COMPRESSION = { 'sstable_compression' : 'LZ4Compressor' }
AND  COMPACTION = { 'class' :  'LeveledCompactionStrategy' };

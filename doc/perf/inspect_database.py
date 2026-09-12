"""Reproduce schema gaps and compare query plans using exported Room v13 DDL.

Uses synthetic data in memory. Never opens a user's application database.
Instruction counts are SQLite VM steps on this desktop, not Android latency.
"""
import json
from pathlib import Path
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / 'core/data/schemas/com.mantrasiddhi.data.local.MantraSiddhiDatabase/13.json'
ENTITIES = json.loads(SCHEMA.read_text(encoding='utf-8'))['database']['entities']
TABLES = {entity['tableName']: entity for entity in ENTITIES}


def database():
    connection = sqlite3.connect(':memory:')
    connection.execute('PRAGMA foreign_keys=ON')
    for entity in ENTITIES:
        connection.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        for index in entity['indices']:
            connection.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    return connection


def insert(connection, table, **values):
    entity = TABLES[table]
    row = {}
    for field in entity['fields']:
        name = field['columnName']
        if name == 'id' and entity['primaryKey']['autoGenerate']:
            continue
        row[name] = ('' if field['affinity'] == 'TEXT' else 0) if field['notNull'] else None
    row.update(values)
    names = ','.join(f'`{name}`' for name in row)
    return connection.execute(f'INSERT INTO `{table}` ({names}) VALUES ({",".join("?" for _ in row)})', list(row.values())).lastrowid


def correctness():
    db = database()
    findings = {}
    # Reproduce the allowed interleaving of async onCreate seed and first real profile.
    new_id = insert(db, 'user_profiles', name='Real account', profileType='INDIVIDUAL', firebaseUid='owner')
    db.execute("INSERT OR REPLACE INTO user_profiles(id,name,profileType,parentId,avatarIndex,firebaseUid,photoUrl,photoSource,useGooglePhoto) VALUES (1,'Arjun','INDIVIDUAL',NULL,0,NULL,NULL,NULL,1)")
    after = db.execute('SELECT name,firebaseUid FROM user_profiles WHERE id=?', (new_id,)).fetchone()
    assert new_id == 1 and after == ('Arjun', None)
    findings['async_seed_interleaving'] = {'real_profile_id': new_id, 'after_seed': after}

    insert(db, 'targets', userId=1, setByUserId=1, stableId='first', isCompleted=0, targetRecitationCount=108)
    insert(db, 'targets', userId=1, setByUserId=1, stableId='second', isCompleted=0, targetRecitationCount=216)
    migration_sql = "UPDATE targets SET stableId = 'legacy-target' WHERE isCompleted = 0"
    migration_source = (ROOT / 'core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt').read_text(encoding='utf-8')
    assert migration_sql in migration_source
    db.execute(migration_sql)
    duplicates = db.execute('SELECT userId,stableId,COUNT(*) FROM targets GROUP BY userId,stableId HAVING COUNT(*)>1').fetchall()
    assert duplicates == [(1, 'legacy-target', 2)]
    findings['migration_identity_assignment'] = duplicates

    for identity in ['entry-a', 'entry-b']:
        insert(db, 'japa_entries', id=identity, userId=1, sessionId=42,
               source='AUDIO_ACCEPTED', quantity=1, mantraId='gayatri', dateKey='20260912', zoneId='UTC')
    duplicate_audio = db.execute('SELECT userId,sessionId,COUNT(*) FROM japa_entries GROUP BY userId,sessionId HAVING COUNT(*)>1').fetchall()
    assert duplicate_audio == [(1, 42, 2)]
    findings['schema_allows_duplicate_audio_association'] = duplicate_audio
    findings['schema_foreign_keys'] = {name: db.execute(f'PRAGMA foreign_key_list(`{name}`)').fetchall() for name in TABLES}
    assert all(not keys for keys in findings['schema_foreign_keys'].values())

    for identity in ['large-a', 'large-b']:
        insert(db, 'japa_entries', id=identity, userId=2, source='SANDHYA', quantity=2**63-1,
               mantraId='gayatri', dateKey='20260912', zoneId='UTC', period='SAYAM')
    total = sum(row[0] for row in db.execute('SELECT quantity FROM japa_entries WHERE userId=2'))
    assert total > 2**63-1
    findings['valid_individual_longs_exceed_period_long_capacity'] = str(total)
    db.close()
    return findings


QUERIES = {
    'active_target': ('SELECT * FROM targets WHERE userId = ? AND isCompleted = 0 AND isActive = 1 ORDER BY startedAtMillis DESC, stableId DESC LIMIT 1', (5,)),
    'audio_entry_lookup': ('SELECT * FROM japa_entries WHERE userId = ? AND sessionId = ? LIMIT 1', (5, 4999)),
    'ordered_japa_history': ('SELECT * FROM japa_entries WHERE userId = ? ORDER BY createdAtMillis, id', (5,)),
    'ordered_session_history': ('SELECT * FROM recitation_sessions WHERE userId = ? ORDER BY id DESC', (5,)),
    'backup_session_page': ('SELECT * FROM recitation_sessions WHERE userId = ? AND id > ? ORDER BY id LIMIT 200', (5, 4500)),
    'backup_change_page': ('SELECT * FROM sandhya_count_changes WHERE userId = ? AND id > ? ORDER BY id LIMIT 200', (5, 'change-04500')),
    'period_change_history': ('SELECT * FROM sandhya_count_changes WHERE userId = ? AND dateKey = ? AND period = ? ORDER BY revision DESC', (5, '20260912', 'SAYAM')),
    'pending_progress_overlay': ('SELECT * FROM cloud_sync_outbox WHERE uid = ? AND kind = ? ORDER BY id', ('owner-5', 'progress')),
    'recent_sessions_existing_good_index': ('SELECT * FROM recitation_sessions WHERE userId = ? ORDER BY createdAt DESC, id DESC LIMIT ?', (5, 20)),
}
INDEXES = [
    'CREATE INDEX audit_targets_active ON targets(userId,isActive,isCompleted,startedAtMillis,stableId)',
    'CREATE UNIQUE INDEX audit_audio_identity ON japa_entries(userId,sessionId)',
    'CREATE INDEX audit_entries_order ON japa_entries(userId,createdAtMillis,id)',
    'CREATE INDEX audit_sessions_id ON recitation_sessions(userId,id)',
    'CREATE INDEX audit_changes_id ON sandhya_count_changes(userId,id)',
    'CREATE INDEX audit_changes_revision ON sandhya_count_changes(userId,dateKey,period,revision)',
    'CREATE INDEX audit_outbox_kind ON cloud_sync_outbox(uid,kind,id)',
]


def measure(db, sql, params):
    plan = [row[3] for row in db.execute('EXPLAIN QUERY PLAN ' + sql, params)]
    steps = 0
    def step():
        nonlocal steps
        steps += 1
        return 0
    db.set_progress_handler(step, 1)
    try:
        rows = db.execute(sql, params).fetchall()
    finally:
        db.set_progress_handler(None, 0)
    return {'plan': plan, 'vm_steps': steps, 'result_rows': len(rows)}


def query_plans():
    db = database()
    # Ten synthetic users with 1,000 records each; deliberately repeated period edits.
    for n in range(1, 10001):
        user = (n-1)//1000+1
        insert(db, 'recitation_sessions', id=n, userId=user, stableId=f'session-{n}', mantraId='gayatri', mode='NORMAL', createdAt=n,
               overallScore=.9, isCorrect=1, wordScoresJson='om:0.9', transcription='om')
        insert(db, 'japa_entries', id=f'entry-{n:05}', userId=user, sessionId=n, createdAtMillis=n, quantity=1,
               source='AUDIO_ACCEPTED', dateKey='20260912', mantraId='gayatri', zoneId='UTC')
        insert(db, 'sandhya_count_changes', id=f'change-{n:05}', userId=user, dateKey='20260912', period='SAYAM',
               revision=n, quantity=n, mode='EDIT', zoneId='UTC')
        insert(db, 'cloud_sync_outbox', id=n, uid=f'owner-{user}', kind='progress' if n%20==0 else 'practice_record', payload='{}')
    for user in range(1, 11):
        for n in range(100):
            insert(db, 'targets', userId=user, setByUserId=user, stableId=f'goal-{user}-{n}', startedAtMillis=n,
                   targetRecitationCount=108, isActive=int(n==99), isCompleted=0)
    db.execute('ANALYZE')
    before = {name: measure(db, *query) for name, query in QUERIES.items()}
    for sql in INDEXES:
        db.execute(sql)
    db.execute('ANALYZE')
    after = {name: measure(db, *query) for name, query in QUERIES.items()}
    db.close()
    return {name: {'sql': QUERIES[name][0], 'params': QUERIES[name][1], 'before': before[name], 'candidate_indexes': after[name]} for name in QUERIES}


def main():
    report = {'sqlite_version': sqlite3.sqlite_version, 'schema': str(SCHEMA.relative_to(ROOT)),
              'table_count': len(TABLES), 'scope': 'synthetic in-memory SQLite; not Android Room execution or latency',
              'correctness_reproductions': correctness(), 'candidate_index_sql': INDEXES, 'query_plans': query_plans()}
    output = Path(__file__).with_name('database-evidence.json')
    output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print('Schema reproductions passed; SQLite', sqlite3.sqlite_version, '; tables:', len(TABLES))
    for name, result in report['query_plans'].items():
        print(name, result['before']['vm_steps'], '->', result['candidate_indexes']['vm_steps'], 'VM steps')
        print('  BEFORE:', '; '.join(result['before']['plan']))
        print('  AFTER: ', '; '.join(result['candidate_indexes']['plan']))
    print('Evidence:', output)


if __name__ == '__main__':
    main()

INSERT INTO job_postings (title, unit_name)
VALUES
    ('공익형', '공익형'),
    ('사회서비스형', '사회서비스형'),
    ('시장형', '시장형');

INSERT INTO ticket_sessions (session_uid, job_id)
SELECT '11111111-1111-4111-8111-111111111111'::uuid, id
FROM job_postings WHERE title = '공익형' AND unit_name = '공익형';

INSERT INTO ticket_sessions (session_uid, job_id)
SELECT '22222222-2222-4222-8222-222222222222'::uuid, id
FROM job_postings WHERE title = '사회서비스형' AND unit_name = '사회서비스형';

INSERT INTO ticket_sessions (session_uid, job_id)
SELECT '33333333-3333-4333-8333-333333333333'::uuid, id
FROM job_postings WHERE title = '시장형' AND unit_name = '시장형';

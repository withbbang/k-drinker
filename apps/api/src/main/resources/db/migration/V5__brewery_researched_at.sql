-- 웹 조사로 빈 칸을 채우려 한 시각. 양조장마다 한 번만 조사하려고 둔다(NULL이면 아직 조사하지 않음).
ALTER TABLE brewery ADD COLUMN researched_at DATETIME(6) NULL;

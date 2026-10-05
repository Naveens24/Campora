-- Reference data: roles, departments and the stop network.
-- Fixed UUIDs keep environments reproducible.

INSERT INTO app_role (id, name, description, created_at, updated_at, created_by, updated_by) VALUES
  ('fa74fd54-ce16-5d3a-87ef-f0cc582a12d6', 'STUDENT', 'Student commuter; can also offer companion rides', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('7b8dc58c-7b6f-52e0-a283-5acdb5040e3c', 'DRIVER', 'Auto / e-rickshaw driver serving campus demand', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('ab63ff5c-2114-526c-b49b-77b8121c616a', 'ADMIN', 'Platform administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

INSERT INTO department (id, code, name, active, created_at, updated_at, created_by, updated_by) VALUES
  ('c543c449-8691-5b24-9968-c93bb520ce9a', 'CSIT', 'Computer Science & Information Technology', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('4d7e9549-7ba6-5dd2-8efe-dda2b1f27b2c', 'MCA', 'Master of Computer Applications', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('333edaf4-1160-5886-8b76-032d67596b93', 'ECE', 'Electronics & Communication Engineering', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('da3a0145-a5b0-553b-ac96-7d13892e0d59', 'ME', 'Mechanical Engineering', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('5373e58c-1a6e-57d7-ab29-cde5536149cf', 'CE', 'Civil Engineering', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('58cabaef-ca94-5699-8012-01d1118c9563', 'CHE', 'Chemical Engineering', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('75bf3030-5e75-519c-befd-c077c0554995', 'MGMT', 'Management Studies', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('7780be00-8819-59d9-b1be-8eb9759da1f3', 'PHARM', 'Pharmacy', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('cf374564-9316-57bc-9fc0-b5549292c4f5', 'PHY', 'Physics', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('510f666d-59ab-597c-9ca2-0bdc5bde6050', 'CHEM', 'Chemistry', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('9db771eb-6be4-533a-b66a-6fb07352311c', 'MATH', 'Mathematics', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('1aee9772-9e82-5642-b22d-f8f704d1ecc2', 'COMM', 'Commerce', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

INSERT INTO destination (id, name, area, type, active, sort_order, created_at, updated_at, created_by, updated_by) VALUES
  ('5337b244-3751-5dbb-a0d2-567aa6559f0e', 'Main Gate', 'GGV Campus, Koni', 'CAMPUS_POINT', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('ce14c2d5-f46b-57c5-9514-aaa6a5669774', 'Engineering Block', 'Institute of Technology', 'CAMPUS_POINT', TRUE, 11, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('71258168-a0c2-57b9-8b78-6793c7d62bea', 'Central Library', 'GGV Campus', 'CAMPUS_POINT', TRUE, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('e7094f52-59d3-58fd-8147-b98ca5467a9b', 'Administrative Block', 'GGV Campus', 'CAMPUS_POINT', TRUE, 13, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('4d188540-905e-5140-8870-f07197476790', 'Science Block', 'School of Physical Sciences', 'CAMPUS_POINT', TRUE, 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('9e4b43ff-7dd5-57ae-95f2-749ab2dfc5cf', 'Management Block', 'School of Studies in Management', 'CAMPUS_POINT', TRUE, 15, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('15bdd0b9-f185-5d0f-bad2-4bf53a7041f1', 'Boys Hostel', 'GGV Campus', 'CAMPUS_POINT', TRUE, 16, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('1bed534c-5249-5597-9816-7701b87c2909', 'Girls Hostel', 'GGV Campus', 'CAMPUS_POINT', TRUE, 17, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('990a4630-d59c-51af-b9a7-76fcf2703fd5', 'Bilaspur Railway Station', 'Bilaspur', 'CITY', TRUE, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('8a37d7e1-95a7-535f-9d18-fbb42f576e0f', 'Old Bus Stand', 'Bilaspur', 'CITY', TRUE, 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('20c4fa0f-f4a0-57ca-8942-05b634ec529f', 'Rajkishore Nagar', 'Bilaspur', 'CITY', TRUE, 102, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('9714faaf-1d9f-5f23-9b40-288ac50e4b4a', 'Nehru Chowk', 'Bilaspur', 'CITY', TRUE, 103, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('f0b65b56-86b0-5cf0-8ddf-50edb4b6afc9', 'Vyapar Vihar', 'Bilaspur', 'CITY', TRUE, 104, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('5e2736bc-b208-56b2-83af-ce4ce516842c', 'Sarkanda', 'Bilaspur', 'CITY', TRUE, 105, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('7ea1b550-e3cc-5b9b-9743-6407e3e82b19', 'Koni Market', 'Koni', 'CITY', TRUE, 106, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
  ('2dec439a-2788-5107-9ebb-bf812d781978', 'Apollo Hospital', 'Seepat Road, Bilaspur', 'CITY', TRUE, 107, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

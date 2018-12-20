INSERT INTO person VALUES ('ABC/11100', 'xxxx-bbbb-cccc-dddd');
INSERT INTO person VALUES ('ABC/22200', 'yyyy-bbbb-cccc-dddd');
INSERT INTO person VALUES ('ABC/33300', 'zzzz-bbbb-cccc-dddd');

INSERT INTO identification VALUES (1, 'ABC/11100', 'Lastname', 'Physical', 'PP', '2011-11-11', 2011);
INSERT INTO identification VALUES (2, 'ABC/22200', 'Last2', 'Moral', 'PM', '2011-11-11', 2011);
INSERT INTO identification VALUES (3, 'ABC/33300', 'Last3', 'Another', 'TR', '2011-11-11', 2011);

INSERT INTO nationality VALUES (1, 'ABC/22200', 'zzzz', 'UA', '2010-11-11', '2020-12-12', 1);
INSERT INTO nationality VALUES (2, 'ABC/33300', 'yyyy1', 'CH', '2000-10-10', '2015-12-12', 1);
INSERT INTO nationality VALUES (3, 'ABC/33300', 'yyyy2', 'FR', '2000-10-10', '2015-12-12', 1);
INSERT INTO nationality VALUES (4, 'ABC/33300', 'yyyy3', 'US', '2000-10-10', '2015-12-12', 1);
INSERT INTO nationality VALUES (5, 'ABC/33300', 'yyyy4', 'CN', '2000-10-10', '2015-12-12', 1);

INSERT INTO relations VALUES ('ABC/11100', 'is spouse of', 'ABC/33300');
INSERT INTO relations VALUES ('ABC/33300', 'is child of', 'ABC/22200');
INSERT INTO relations VALUES ('ABC/22200', 'is parent of', 'ABC/11100');

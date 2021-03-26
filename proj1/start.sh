echo "--------- SDIS T1G06 ---------"

echo "--------- Starting Peer 1 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 1 ServiceInterface 224.0.0.4 4441 224.0.0.2 4442 224.0.0.3 4443

echo "--------- Starting Peer 2 ---------"
start java -Djava.rmi.server.codebase=src/out/production/proj1/sdis/t1g06/ -Djava.security.manager -Djava.security.policy=src/rmipolicy/my.policy/ -classpath src/out/production/proj1 sdis.t1g06.Peer 1.0 2 ServiceInterface 224.0.0.4 4441 224.0.0.2 4442 224.0.0.3 4443

sleep 1

echo "--------- Starting testApp ---------"
start java  -classpath src/out/production/proj1 sdis.t1g06.TestApp 1 BACKUP teste.txt 1
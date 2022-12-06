create table PostalCodes(
id varchar(100) not null primary key, 
population int, 
area int
);

create table DistributionHubs(
id varchar(100) not null primary key, 
x int, 
y int, 
repairTime float, 
inService boolean
);

create table PostalHubRelation(
postalId varchar(100) not null,
hubId varchar(100) not null,
primary key (postalId, hubId),
foreign key (hubId) references DistributionHubs(id)
);

create table RepairLog(
id int not null auto_increment primary key,
emp_id varchar(100) not null,
hubId varchar(100) not null,
repairTime float,
hubRestored boolean,
foreign key (hubId) references DistributionHubs(id)
);




StreamSets Data Collector Origin for the Salesforce Bulk API
============================================================

The StreamSets Salesforce Bulk API origin allows you to read data from Salesforce into StreamSets Data Collector (SDC). The origin uses the Bulk API to periodically run queries against Salesforce, in either incremental mode, by default using the `Id` system field to as an offset, or full mode, reading the entire dataset with each query.

At the time of writing, the Salesforce Bulk API origin is in its early stages of development. Feel free to use it, and [report any issues](https://github.com/metadaddy/StreamSets-SalesforceBulkAPI/issues) that you might find.

Pre-requisites
--------------

You will need the following:

* [StreamSets Data Collector](https://streamsets.com/product/)

* A Salesforce Environment ('org'). For development and evaluation, the easiest way to get one is via the [Developer Edition signup page](https://developer.salesforce.com/signup).

Installation
------------

Download the [Salesforce Bulk API origin tarball](https://github.com/metadaddy/StreamSets-SalesforceBulkAPI/blob/master/target/force-lib-1.0-SNAPSHOT.jar?raw=true) and extract it in the SDC user-libs directory:

	$ cd path-to-sdc/user-libs
	$ tar xvfz force-lib-1.0-SNAPSHOT.jar

Configuration
-------------

| Force.com Property | Description |
| --- | --- |
| Username | The username for your Salesforce org. |
| Password | The password for your Salesforce org. For security, we recommend you store the password in a plain text file (no carriage return/newline) in SDC's `resources` directory and reference it as `${runtime:loadResource('wavePassword.txt',true)}` |
| Auth Endpoint | Salesforce SOAP API Authentication Endpoint: `login.salesforce.com` for production/Developer Edition, `test.salesforce.com` for sandboxes |
| API Version | Salesforce SOAP API Version. Defaults to the current version (36.0). |
| Incremental Mode | Defines how the Salesforce Bulk API origin queries Salesforce. Select to perform incremental queries. Clear to perform full queries. Default is incremental mode. |
| SOQL Query | SOQL query to use when reading data from the database. The offset field (see below) must be listed first in both the `WHERE` and `ORDER BY` clauses.|
| Initial Offset | Offset value to use when the pipeline starts. |
| Offset Field | Field to use for the offset value. |
| Query Interval | Amount of time to wait between queries. Enter an expression based on a unit of time. You can use `SECONDS`, `MINUTES`, or `HOURS`. Default is 1 minute: `${1 * MINUTES}`. Since Salesforce API calls are a limited resource, you should carefully balance API call consumption against data freshness.|

Since the origin calls out to Salesforce, you will need to add a section to the security policy file in `path-to-sdc/etc/sdc-security.policy`:

	grant codebase "file://${sdc.dist.dir}/user-libs/force-lib/-" {
	  permission java.net.SocketPermission "*", "connect, resolve";
	};

Operation
---------

Configure the Salesforce Bulk API origin as detailed above. Since the `Id` system field is automatically assigned in increasing order as each Salesforce record is created, it is ideal for use as the offset field. For example, to read Opportunity records, you might use a SOQL query such as:

	SELECT AccountId, Amount, CampaignId, CloseDate, CreatedById, 
		CreatedDate, CurrentGenerators__c, 
		DeliveryInstallationStatus__c, Description, ExpectedRevenue, 
		Fiscal, FiscalQuarter, FiscalYear, ForecastCategory, 
		ForecastCategoryName, HasOpenActivity, HasOpportunityLineItem, 
		HasOverdueTask, Id, IsClosed, IsDeleted, IsPrivate, IsWon, 
		LastActivityDate, LastModifiedById, LastModifiedDate, 
		LastReferencedDate, LastViewedDate, LeadSource, 
		MainCompetitors__c, Name, NextStep, OrderNumber__c, OwnerId, 
		Pricebook2Id, Probability, StageName, SystemModstamp, 
		TotalOpportunityQuantity, TrackingNumber__c, Type 
	FROM Opportunity 
	WHERE Id > '${OFFSET}' 
	ORDER BY Id

Caveats
-------

Since this origin uses the Salesforce Bulk API, it is subject to the same [limits and considerations](https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/asynch_api_concepts_limits.htm), in particular governor limits and limitations on Bulk API queries. For example, the Bulk API can’t access or query compound address or compound geolocation fields.

Video
-----

This video shows the Salesforce Bulk API origin in action:

[![YouTube video](http://img.youtube.com/vi/YkyBrmOz5P8/maxresdefault.jpg)](//www.youtube.com/watch?v=YkyBrmOz5P8)

Future Work
-----------

A future version of this origin will include support for subscribing to change notifications via the Salesforce Streaming API rather than issuing repeated queries.
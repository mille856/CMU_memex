TJBatchExtractor
=================
note: requires java 7. (developed and tested on openjdk-1.7), will fail on java 8.

Usage:
Compile using
\$ javac -classpath '.:./dependencies/*' TJBatchExtractor.java

Run using
\$ java -classpath '.:./dependencies/*' TJBatchExtractor [num_threads] [textfile] [outfile]


This project is a regular expression based information extractor designed to operate on
text captured from female escort advertisements originating from {US}
sections of Backpage.com. The motivation is to extract domain specific
information that may be representative of individuals or groups
responsible for each advertisement. To that end, the information extracted
focuses on physical description and contact information.

The system attempts to extract occurrences of the following informational elements.

Perspective_1st: Count of 1st person pronouns

Perspective_3rd: Count of 3rd person pronouns

Name: Female first names

Age: Age

Cost: Dollar figure charged for various services. Notation is given as Dollar/Measure/Unit. Dollar represents a cost, Unit represents object of the cost (e.g. hours, minutes, short stay, special, etc.), Measure represents the number of units (e.g. 30 minutes, 1 hour, hhr, etc.)

Height_ft: Height in feet, multiple values correlate with multiple values of Height_in

Height_in: Remaining inches of height, correlates with Height_ft

Weight: Weight in lbs

Cup: Cup size

Chest: Chest measurement

Waist: Waist measurement

Hip: Hip measurement

Ethnicity: Country referenced ethnicity (e.g. Spanish, Russian, etc.)

SkinColor: Color of skin

EyeColor: Color of eyes

HairColor: Color of hair

Restriction_Type: One of [no, over]; the type of restriction, i.e. "no black men", or "only men over 45."

Restriction_Ethnicity: The ethnicity/ skin color restricted

Restriction_Age: The threshold age value for the over restrictions

PhoneNumber: Phone number

AreaCode_State: State associated with phone number's area code

AreaCode_Cities: Cities/ locations associated with phone number's area code

Email: Email address

Url: urls specifically referenced or linked to in the body

Media: iframes and other foreign sourced content



grammar calculator_example;

program: expr EOF;

expr: x_minus | x_plus;

x_minus: x_times '-' expr;

x_plus: x_times ('+' expr)*;

x_times: x_other ('*' x_other)*;

x_other: INT | '(' expr ')';

INT: [0-9]+ ;

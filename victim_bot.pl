:- use_module(library(socket)).
:- use_module(library(http/json)).
:- use_module(library(random)).
:- use_module(library(lists)).

:- dynamic bot_position/2.
:- dynamic maniac_position/2.

connect_bot :-
    tcp_connect(localhost:12345, Stream, [type(text)]),
    set_stream(Stream, encoding(utf8)),
    format(Stream, 'victim~n', []),
    flush_output(Stream),
    thread_create(update_listener(Stream), _, [detached(true)]),
    bot_loop(Stream).

update_listener(Stream) :-
    repeat,
        (   read_line_to_string(Stream, Response)
        ->  process_response(Response),
            display_map(Response)
        ;   format('Server disconnected~n', []),
            close(Stream),
            !
        ),
    fail.

process_response(Response) :-
    open_string(Response, Stream),
    json_read_dict(Stream, JsonTerm, [value_string_as(atom)]),
    close(Stream),
    (   is_dict(JsonTerm), get_dict(map, JsonTerm, MapLines)
    ->  maplist(process_map_line, MapLines),
        find_positions(MapLines)
    ;   true).

process_map_line(Line) :-
    split_string(Line, "", "", Chars),
    process_line_chars(Chars, 0).

process_line_chars([], _).
process_line_chars([C|Rest], X) :-
    (   C = "☺" -> assertz(bot_position(X, Y))
    ;   C = "☠" -> assertz(maniac_position(X, Y))
    ;   true),
    NextX is X + 1,
    process_line_chars(Rest, NextX).

find_positions(MapLines) :-
    retractall(bot_position(_, _)),
    retractall(maniac_position(_, _)),
    find_positions(MapLines, 0).

find_positions([], _).
find_positions([Line|Rest], Y) :-
    string_chars(Line, Chars),
    process_line_chars(Chars, 0, Y),
    NextY is Y + 1,
    find_positions(Rest, NextY).

process_line_chars([], _, _).
process_line_chars([C|Rest], X, Y) :-
    (   C = '☺' -> assertz(bot_position(X, Y))
    ;   C = '☠' -> assertz(maniac_position(X, Y))
    ;   true),
    NextX is X + 1,
    process_line_chars(Rest, NextX, Y).

bot_loop(Stream) :-
    get_time(_),
    (   bot_position(X, Y),
        maniac_position(MX, MY)
    ->  calculate_direction(X, Y, MX, MY, Dir),
        format(Stream, '~w~n', [Dir]),
        flush_output(Stream),
        sleep(0.5)
    ;   random_member(Dir, ['w','a','s','d']),
        format(Stream, '~w~n', [Dir]),
        flush_output(Stream),
        sleep(1)),
    bot_loop(Stream).

calculate_direction(BX, BY, MX, MY, Dir) :-
    DX is BX - MX,
    DY is BY - MY,
    (   abs(DX) > abs(DY) ->
        (   DX > 0 -> try_move('d', 'a', Dir)
        ;            try_move('a', 'd', Dir))
    ;   DY > 0 -> try_move('s', 'w', Dir)
    ;            try_move('w', 's', Dir)),
    (   valid_move(Dir)
    ->  true
    ;   random_member(Dir, ['w','a','s','d'])).

try_move(Primary, Secondary, Dir) :-
    (   valid_move(Primary)
    ->  Dir = Primary
    ;   valid_move(Secondary)
    ->  Dir = Secondary
    ;   random_member(Dir, ['w','a','s','d'])).

valid_move(Dir) :-
    bot_position(X, Y),
    new_position(Dir, X, Y, NX, NY),
    map_cell(NX, NY, Cell),
    \+ wall_char(Cell).

new_position('w', X, Y, X, NY) :- NY is Y - 1.
new_position('s', X, Y, X, NY) :- NY is Y + 1.
new_position('a', X, Y, NX, Y) :- NX is X - 1.
new_position('d', X, Y, NX, Y) :- NX is X + 1.

wall_char('║'). wall_char('═'). wall_char('╣'). wall_char('╔'). 
wall_char('╗'). wall_char('╝'). wall_char('╚'). wall_char('╦').

map_cell(X, Y, Cell) :-
    current_map_lines(Lines),
    nth0(Y, Lines, Line),
    string_chars(Line, Chars),
    nth0(X, Chars, Cell).

display_map(JsonString) :-
    open_string(JsonString, Stream),
    json_read_dict(Stream, JsonTerm, [value_string_as(atom)]),
    close(Stream),
    (   is_dict(JsonTerm), get_dict(map, JsonTerm, Map)
    ->  retractall(current_map_lines(_)),
        assertz(current_map_lines(Map)),
        maplist(format('~s~n'), Map)
    ;   true).

:- connect_bot.
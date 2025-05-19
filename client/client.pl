:- use_module(library(socket)).
:- use_module(library(http/json)).
:- use_module(library(thread)).

connect_to_server :-
    tcp_connect(localhost:12345, Stream, [type(text)]),
    set_stream(Stream, encoding(utf8)),

    choose_role(Stream, _Role),
    format('Connected. Use WASD to move, E to place mine, Q to quit~n~n', []),
    
    thread_create(update_listener(Stream), _, [detached(true)]),
    
    client_loop(Stream).

update_listener(Stream) :-
    repeat,
        (   read_line_to_string(Stream, Response)
        ->  format('~n~n~n~n~n~n~n~n~n~n~n', []),
            display_map(Response)
        ;   format('Server disconnected~n', []),
            close(Stream),
            !
        ),
    fail.

choose_role(Stream, Role) :-
    format('Choose your role:~n1 - Victim~n2 - Maniac~n', []),
    get_single_char(Input),
    (   Input =:= 0'1 -> Role = victim
    ;   Input =:= 0'2 -> Role = maniac
    ;   format('Invalid choice. Defaulting to victim.~n', []), Role = victim
    ),
    format(Stream, '~w~n', [Role]),
    flush_output(Stream).

display_map(JsonString) :-
    open_string(JsonString, Stream),
    json_read_dict(Stream, JsonTerm, [value_string_as(atom)]),
    close(Stream),
    (   is_dict(JsonTerm), get_dict(map, JsonTerm, Map)
    ->  maplist(format('~s~n'), Map)
    ;   format('Invalid data format: ~w~n', [JsonString])
    ).

client_loop(Stream) :-
    get_single_char(Char),
    (   char_code('q', Char)
    ->  format('Quitting...~n', []),
        close(Stream)
    ;   (   memberchk(Char, [0'w, 0's, 0'a, 0'd, 0'e])
        ->  format(Stream, '~c~n', [Char]),
            flush_output(Stream)
        ;   true
        ),
        client_loop(Stream)
    ).

:- connect_to_server.
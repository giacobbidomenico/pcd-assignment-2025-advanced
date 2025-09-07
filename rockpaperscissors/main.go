package main

import (
	"fmt"
	"math/rand"
	"os"
	"os/signal"
	"time"
)

type Move int

const (
	Rock Move = iota
	Paper
	Scissors
)

func player(id int, name string, request <-chan bool, moveOut chan<- Move, result <-chan int, r *rand.Rand) {
	score := 0
	for {
		<-request
		move := Move(r.Intn(3))
		moveOut <- move
		winner := <-result
		switch winner {
		case 0:
			fmt.Printf("%s: Tie. Score: %d\n", name, score)
		case id:
			score++
			fmt.Printf("%s: I won! Score: %d\n", name, score)
		default:
			fmt.Printf("%s: I lost. Score: %d\n", name, score)
		}
	}
}

func referee(req1 chan<- bool, req2 chan<- bool, move1 <-chan Move, move2 <-chan Move, res1 chan<- int, res2 chan<- int) {
	for i := 1; ; i++ {
		fmt.Printf("\nRound %d\n", i)

		req1 <- true
		req2 <- true

		m1 := <-move1
		m2 := <-move2
		var winner int
		if m1 == m2 {
			winner = 0
		} else if (m1 == Rock && m2 == Scissors) || (m1 == Scissors && m2 == Paper) || (m1 == Paper && m2 == Rock) {
			winner = 1
		} else {
			winner = 2
		}
		res1 <- winner
		res2 <- winner

		time.Sleep(1 * time.Second)
	}
}

func main() {
	source := rand.NewSource(time.Now().UnixNano())
	r := rand.New(source)

	req1 := make(chan bool)
	req2 := make(chan bool)
	move1 := make(chan Move)
	move2 := make(chan Move)
	res1 := make(chan int)
	res2 := make(chan int)

	go player(1, "Player 1", req1, move1, res1, r)
	go player(2, "Player 2", req2, move2, res2, r)
	go referee(req1, req2, move1, move2, res1, res2)

	c := make(chan os.Signal, 1)
	signal.Notify(c, os.Interrupt)
	<-c
	fmt.Println("\nGame stopped.")
}

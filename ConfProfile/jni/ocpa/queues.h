/*
 * queues.h
 *
 *      Author: Dmitry Vorobiev
 */

#ifndef QUEUES_H_
#define QUEUES_H_

#include <stdlib.h>
#include <stdbool.h>

typedef struct queue queue;
typedef struct queue_link queue_link;

struct queue {
	queue_link* head_link;
	queue_link* tail_link;
};

struct queue_link {
	queue_link* next;
	void* buff;
	int size;
};

inline queue_link* queue_link_init();
inline bool queue_link_deinit(queue_link* ql);
inline queue* queue_init();
inline queue_link* queue_get(queue* q);
inline bool queue_put(queue* q, queue_link* ql);
inline bool queue_deinit(queue* q);

#endif /* QUEUES_H_ */

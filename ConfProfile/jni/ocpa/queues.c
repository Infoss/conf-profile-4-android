/*
 * queues.c
 *
 *      Author: Dmitry Vorobiev
 */

#include "queues.h"
#include "android_log_utils.h"


inline queue_link* queue_link_init() {
	queue_link* ql = malloc(sizeof(queue_link));
	if(ql != NULL) {
		ql->next = NULL;
		ql->buff = NULL;
		ql->size = 0;
	}

	return ql;
}

inline bool queue_link_deinit(queue_link* ql) {
	if(ql == NULL) {
		return false;
	}

	if(ql->buff != NULL) {
		free(ql->buff);
	}

	ql->next = NULL;
	ql->buff = NULL;
	ql->size = 0;
	free(ql);

	return true;
}

inline queue* queue_init() {
	queue* q = malloc(sizeof(queue));
	if(q != NULL) {
		q->head_link = NULL;
		q->tail_link = NULL;
	}

	return q;
}

inline queue_link* queue_get(queue* q) {
	if(q == NULL || q->head_link == NULL) {
		return NULL;
	}

	queue_link* ql = q->head_link;
	q->head_link = ql->next;
	if(ql == q->tail_link) {
		//this lisk is the one
		q->tail_link = NULL;
	}

	ql->next = NULL;

	return ql;
}

inline bool queue_put(queue* q, queue_link* ql) {
	if(q == NULL || ql == NULL) {
		return false;
	}

	ql->next = NULL;
	if(q->tail_link != NULL) {
		q->tail_link->next = ql;
	} else {
		q->head_link = ql;
	}
	q->tail_link = ql;

	return true;
}

inline bool queue_deinit(queue* q) {
	if(q == NULL) {
		return false;
	}

	queue_link* ql;
	while((ql = queue_get(q)) != NULL) {
		queue_link_deinit(ql);
	}

	q->head_link = NULL;
	q->tail_link = NULL;
	free(q);

	return true;
}


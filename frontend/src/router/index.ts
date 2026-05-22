import { createRouter, createWebHistory } from 'vue-router'
import AppSidebar from '../components/AppSidebar.vue'
import Home from '../views/Home.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      components: { default: Home, sidebar: AppSidebar },
    },
    {
      path: '/ontologies',
      name: 'ontology-list',
      components: {
        default: () => import('../views/ontology/OntologyList.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/ontologies/create',
      name: 'ontology-create',
      components: {
        default: () => import('../views/ontology/OntologyCreate.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/ontologies/import',
      name: 'ontology-import',
      components: {
        default: () => import('../views/ontology/OntologyImport.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/ontologies/:id',
      name: 'ontology-detail',
      components: {
        default: () => import('../views/ontology/OntologyDetail.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/ontologies/:ontologyId/classes/:classId',
      name: 'class-detail',
      components: {
        default: () => import('../views/ontology/ClassDetail.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/ontologies/:id/query',
      name: 'ontology-query',
      components: {
        default: () => import('../views/ontology/QueryEngine.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/query-test',
      name: 'query-test',
      components: {
        default: () => import('../views/QueryTest.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/db-connections',
      name: 'db-connections',
      components: {
        default: () => import('../views/database/DbConnections.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/db-connections/:connId/import',
      name: 'table-import',
      components: {
        default: () => import('../views/database/TableImport.vue'),
        sidebar: AppSidebar,
      },
    },
    {
      path: '/services/semantic-query',
      name: 'service-api',
      components: {
        default: () => import('../views/ServiceApi.vue'),
        sidebar: AppSidebar,
      },
    },
  ],
})

export default router
